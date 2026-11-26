package com.linpj.novel.create.consumer;

import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.pojo.entity.NovelKbSource;
import com.linpj.novel.create.service.KnowledgeBaseService;
import com.linpj.novel.create.service.ingest.EmbeddingIndexService;
import com.linpj.novel.create.service.ingest.KbSourceService;
import com.linpj.novel.create.utils.FileUtil;
import com.linpj.novel.create.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库文档上传消费者（document-upload）
 *
 * M2 重设计：走 ingest 统一入口（novel_kb_source + novel_rag_chunk + 向量投影），
 * 不再直写 vector_store / knowledge_base.document_ids。
 *
 * 批量消费 + 手动确认模式（与 application.yml 的 listener.type: batch 对齐）。
 * 单条消息处理失败不阻塞批次：记录日志后继续，避免毒消息卡死队列。
 *
 * @author HL
 */
@Component
public class EventConsumer {

    private final FileUtil fileUtil;

    private static final Logger logger = LoggerFactory.getLogger(EventConsumer.class);

    private final KnowledgeBaseService knowledgeBaseService;

    private final KbSourceService kbSourceService;

    private final EmbeddingIndexService embeddingIndexService;

    public EventConsumer(FileUtil fileUtil, KnowledgeBaseService knowledgeBaseService,
                         KbSourceService kbSourceService, EmbeddingIndexService embeddingIndexService) {
        this.fileUtil = fileUtil;
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbSourceService = kbSourceService;
        this.embeddingIndexService = embeddingIndexService;
    }

    /**
     * 批量消费签名（List 签名是 batch 容器的硬性要求，单条签名会在运行时抛异常）
     */
    @KafkaListener(topics = {"document-upload"}, groupId = "adminGroup", concurrency = "5")
    public void onEvent(List<String> events, Acknowledgment ack) {
        for (String event : events) {
            try {
                processEvent(event);
            } catch (Exception e) {
                // 单条失败不阻塞批次；失败消息不做重投（避免毒消息反复消费），由日志与业务侧补偿
                logger.error("文档上传事件处理失败: {}", event, e);
            }
        }
        ack.acknowledge();
    }

    private void processEvent(String event) {
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> map = JsonUtil.fromJson(event, type);
        // 生产者写入的字段是 file（MinIO URL 字符串）与 knowledgeBaseId（数字）
        Object fileUrlObj = map.get("file");
        Object knowledgeIdObj = map.get("knowledgeBaseId");
        if (!(fileUrlObj instanceof String fileUrl) || !(knowledgeIdObj instanceof Number knowledgeId)) {
            logger.warn("文档上传事件缺少 file/knowledgeBaseId 字段或类型不符，跳过: {}", event);
            return;
        }
        long knowledgeBaseId = knowledgeId.longValue();
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(knowledgeBaseId);
        if (knowledgeBase == null) {
            logger.warn("文档上传事件引用的知识库不存在，跳过: knowledgeBaseId={}", knowledgeBaseId);
            return;
        }

        Map<String, String> params = fileUtil.generateParams(fileUrl);
        String objectName = params.get("objectName");

        // 解析文件
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload_", "_" + objectName);
            String content;
            try (InputStream inputStream = fileUtil.downloadByUrlAsStream(fileUrl)) {
                content = extractText(inputStream, tempFile);
            }
            if (content == null || content.isBlank()) {
                logger.warn("文档内容为空或解析失败: file={}", fileUrl);
                return;
            }
            NovelKbSource source = kbSourceService.upsertUpload(
                    knowledgeBase.getBookId(), knowledgeBase.getUserId(), objectName, fileUrl);
            int chunks = embeddingIndexService.indexSourceContent(
                    knowledgeBase.getBookId(), source.getId(), content);
            kbSourceService.markReady(source, chunks);
            logger.info("文档素材入库: bookId={}, sourceId={}, file={}, chunks={}",
                    knowledgeBase.getBookId(), source.getId(), objectName, chunks);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }

    private String extractText(InputStream inputStream, File tempFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        DocumentReader reader = new TikaDocumentReader("file:" + tempFile.getAbsolutePath());
        List<Document> documents = reader.get();
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }
}

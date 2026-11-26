package com.linpj.novel.create.consumer;

import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.service.KnowledgeBaseService;
import com.linpj.novel.create.utils.FileUtil;
import com.linpj.novel.create.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
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
import java.util.stream.Stream;

/**
 * 知识库文档上传消费者（document-upload）
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

    private final PgVectorStore pgVectorStore;

    private final KnowledgeBaseService knowledgeBaseService;

    private final TokenTextSplitter tokenTextSplitter;


    public EventConsumer(FileUtil fileUtil, PgVectorStore pgVectorStore, KnowledgeBaseService knowledgeBaseService) {
        this.fileUtil = fileUtil;
        this.pgVectorStore = pgVectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenTextSplitter = new TokenTextSplitter();
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
            List<Document> documentList;
            try (InputStream inputStream = fileUtil.downloadByUrlAsStream(fileUrl)) {
                documentList = getDocuments(inputStream, tempFile, knowledgeBaseId, objectName, knowledgeBase.getBookId());
            }
            logger.info("{} documents loaded and split from uploaded file", documentList.size());
            dataInsert(knowledgeBaseId, documentList, documentList.stream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }

    private void dataInsert(Long knowledgeBaseId, List<Document> documentList, Stream<Document> stream) {
        pgVectorStore.doAdd(documentList);
        List<String> list = stream.map(Document::getId).toList();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setDocumentIds(list);
        boolean b = knowledgeBaseService.updateById(knowledgeBase);
        if (!b) {
            logger.error("知识库 documentIds 回写失败: knowledgeBaseId={}", knowledgeBaseId);
        }
    }

    private List<Document> getDocuments(InputStream inputStream, File tempFile, Long knowledgeBaseId, String originalFilename, Long bookId) throws IOException {
        // 将输入流内容写入临时文件
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        // 使用正确的构造函数处理文件系统路径
        TikaDocumentReader reader = new TikaDocumentReader("file:" + tempFile.getAbsolutePath());
        List<Document> documentList = reader.get();
        List<Document> documentsWithMetadata = documentList.stream()
                .peek((document) -> {
                    document.getMetadata().put("source", "uploaded_file");
                    document.getMetadata().put("upload_time", System.currentTimeMillis());
                    document.getMetadata().put("filename", originalFilename);
                    document.getMetadata().put("knowledgeId", knowledgeBaseId);
                    // 书级隔离键：检索按本次创作所属书籍过滤（必须为数值，避免过滤不命中）
                    document.getMetadata().put("bookId", bookId);
                })
                .toList();
        return tokenTextSplitter.split(documentsWithMetadata);
    }

}

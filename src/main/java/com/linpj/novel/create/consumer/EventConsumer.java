package com.linpj.novel.create.consumer;

import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.service.KnowledgeBaseService;
import com.linpj.novel.create.service.impl.CustomizeKnowledgeBaseServiceImpl;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author HL
 */
@Component
public class EventConsumer {

    private final FileUtil fileUtil;

    private static final Logger logger = LoggerFactory.getLogger(CustomizeKnowledgeBaseServiceImpl.class);

    private final PgVectorStore pgVectorStore;

    private final KnowledgeBaseService knowledgeBaseService;

    private final TokenTextSplitter tokenTextSplitter;


    public EventConsumer(FileUtil fileUtil, PgVectorStore pgVectorStore, KnowledgeBaseService knowledgeBaseService) {
        this.fileUtil = fileUtil;
        this.pgVectorStore = pgVectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenTextSplitter = new TokenTextSplitter();
    }

    @KafkaListener(topics = {"document-upload"}, groupId = "adminGroup", concurrency = "5")
    public void onEvent(String event, Acknowledgment ack) {
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> map = JsonUtil.fromJson(event, type);
        String fileUrl = (String) map.get("file");
        long knowledgeBaseId = (long) map.get("knowledgeId");
        File tempFile = null;
        Map<String, String> params = fileUtil.generateParams(fileUrl);
        String objectName = params.get("objectName");

        //解析文件
        try {
            tempFile = File.createTempFile("upload_", "_" + objectName);
            InputStream inputStream = fileUtil.downloadByUrlAsStream(fileUrl);
            List<Document> documentList = getDocuments(inputStream, tempFile, knowledgeBaseId, objectName);
            logger.info("{} documents loaded and split from uploaded file", documentList.size());
            dataInsert(knowledgeBaseId, documentList, documentList.stream());
            ack.acknowledge();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            tempFile.deleteOnExit();
        }
    }

    private void dataInsert(Long knowledgeBaseId, List<Document> documentList, Stream<Document> stream) {
        pgVectorStore.doAdd(documentList);
        List<String> list = stream.map(Document::getId).toList();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setDocumentIds(list);
        boolean b = knowledgeBaseService.updateById(knowledgeBase);
        ThrowsUtils.throwIf(!b, ErrorCode.SYSTEM_ERROR);
    }

    private List<Document> getDocuments(InputStream inputStream, File tempFile, Long knowledgeBaseId, String originalFilename) throws IOException {
        tempFile.deleteOnExit();

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
                })
                .toList();
        return tokenTextSplitter.split(documentsWithMetadata);
    }

}

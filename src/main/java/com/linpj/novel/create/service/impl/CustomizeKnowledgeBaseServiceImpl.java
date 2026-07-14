package com.linpj.novel.create.service.impl;


import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentCloudReader;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.service.CustomizeKnowledgeBaseService;
import com.linpj.novel.create.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author HL
 */
@Service
public class CustomizeKnowledgeBaseServiceImpl implements CustomizeKnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(CustomizeKnowledgeBaseServiceImpl.class);

    private final PgVectorStore pgVectorStore;

    private final KnowledgeBaseService knowledgeBaseService;

    private final TokenTextSplitter tokenTextSplitter;

    public CustomizeKnowledgeBaseServiceImpl(PgVectorStore pgVectorStore, KnowledgeBaseService knowledgeBaseService) {
        this.pgVectorStore = pgVectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenTextSplitter = new TokenTextSplitter();
    }

    @Override
    public void uploadFile(MultipartFile file, Long knowledgeBaseId) {
        // 校验知识库存不存在
        KnowledgeBase byId = knowledgeBaseService.getById(knowledgeBaseId);
        if (byId == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库不存在");
        }
        File tempFile = null;
        //解析文件
        try {
            tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            List<Document> documentList = getDocuments(file, tempFile, knowledgeBaseId);
            logger.info("{} documents loaded and split from uploaded file", documentList.size());
            dataInsert(knowledgeBaseId, documentList, documentList.stream());
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

    @Override
    public void uploadContent(String content, Long knowledgeBaseId) {
        Document documents = new Document(content);
        List<Document> documentList = tokenTextSplitter.split(documents);
        List<Document> documentsWithMetadata = documentList.stream()
                .peek((document) -> {
                    document.getMetadata().put("source", "upload_content");
                    document.getMetadata().put("upload_time", System.currentTimeMillis());
                    document.getMetadata().put("knowledgeId", knowledgeBaseId);
                })
                .toList();
        dataInsert(knowledgeBaseId, documentsWithMetadata, documentList.stream());
    }

    private List<Document> getDocuments(MultipartFile file, File tempFile, Long knowledgeBaseId) throws IOException {
        tempFile.deleteOnExit();

        // 将上传的文件内容写入临时文件
        try (InputStream inputStream = file.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(tempFile)) {
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
                    // 手动设置数字ID
                    // 添加自定义元数据
                    document.getMetadata().put("source", "uploaded_file");
                    document.getMetadata().put("upload_time", System.currentTimeMillis());
                    document.getMetadata().put("filename", file.getOriginalFilename());
                    document.getMetadata().put("knowledgeId", knowledgeBaseId);
                })
                .toList();
        return tokenTextSplitter.split(documentsWithMetadata);
    }
}
package com.linpj.novel.create.service.impl;


import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.pojo.entity.NovelKbSource;
import com.linpj.novel.create.service.CustomizeKnowledgeBaseService;
import com.linpj.novel.create.service.KnowledgeBaseService;
import com.linpj.novel.create.service.ingest.EmbeddingIndexService;
import com.linpj.novel.create.service.ingest.KbSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库素材入库（M2 重设计：chunk 权威记录 + 向量投影）
 *
 * <p>与旧实现的差异：切块/打标/写向量全部收敛到 ingest 包（ChunkingService + EmbeddingIndexService），
 * 素材文本持久化在 novel_kb_source + novel_rag_chunk，可随时重建向量。
 *
 * @author HL
 */
@Service
public class CustomizeKnowledgeBaseServiceImpl implements CustomizeKnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(CustomizeKnowledgeBaseServiceImpl.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbSourceService kbSourceService;
    private final EmbeddingIndexService embeddingIndexService;

    public CustomizeKnowledgeBaseServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                              KbSourceService kbSourceService,
                                              EmbeddingIndexService embeddingIndexService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbSourceService = kbSourceService;
        this.embeddingIndexService = embeddingIndexService;
    }

    @Override
    public void uploadFile(MultipartFile file, Long knowledgeBaseId) {
        KnowledgeBase byId = knowledgeBaseService.getById(knowledgeBaseId);
        if (byId == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库不存在");
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            String content = extractText(file, tempFile);
            ingestUpload(byId, file.getOriginalFilename(), content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (tempFile != null) {
                tempFile.deleteOnExit();
            }
        }
    }

    @Override
    public void uploadContent(String content, Long knowledgeBaseId) {
        KnowledgeBase byId = knowledgeBaseService.getById(knowledgeBaseId);
        if (byId == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库不存在");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容不能为空");
        }
        NovelKbSource source = kbSourceService.createManual(byId.getBookId(), byId.getUserId(), content);
        int chunks = embeddingIndexService.indexSourceContent(byId.getBookId(), source.getId(), content);
        kbSourceService.markReady(source, chunks);
        logger.info("文本素材入库: bookId={}, sourceId={}, chunks={}",
                byId.getBookId(), source.getId(), chunks);
        if (chunks == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "素材内容为空或无法切分");
        }
    }

    /** 文件素材：同名重传 = 替换（uk_kb_source_file 语义） */
    private void ingestUpload(KnowledgeBase kb, String fileName, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容为空或无法解析");
        }
        NovelKbSource source = kbSourceService.upsertUpload(kb.getBookId(), kb.getUserId(), fileName, null);
        int chunks = embeddingIndexService.indexSourceContent(kb.getBookId(), source.getId(), content);
        kbSourceService.markReady(source, chunks);
        logger.info("文件素材入库: bookId={}, sourceId={}, file={}, chunks={}",
                kb.getBookId(), source.getId(), fileName, chunks);
        if (chunks == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容为空或无法切分");
        }
    }

    private String extractText(MultipartFile file, File tempFile) throws IOException {
        tempFile.deleteOnExit();
        try (InputStream inputStream = file.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
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

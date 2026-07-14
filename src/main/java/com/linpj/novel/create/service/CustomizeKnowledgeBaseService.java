package com.linpj.novel.create.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author HL
 */
public interface CustomizeKnowledgeBaseService {
    void uploadFile(MultipartFile multipartFile, Long knowledgeBaseId) throws IOException;

    void uploadContent(String content, Long knowledgeBaseId);




}

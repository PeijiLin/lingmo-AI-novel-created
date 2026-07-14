package com.linpj.novel.create.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.knowledge.KnowledgeBaseMapper;
import com.linpj.novel.create.pojo.common.PageRequest;
import com.linpj.novel.create.pojo.dto.KnowledgeBaseDto;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.pojo.vo.KnowledgeBaseVo;
import com.linpj.novel.create.service.KnowledgeBaseService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author HL
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

//    /**
//     * 使用的索引名称，用于标识 DashScope 中的向量存储空间。
//     */
//    @Value("${novel.rag.index-name}")
//    private String indexName;
//
//    /**
//     * Spring AI 快速入门 PDF 资源文件路径。
//     */
//    @Value("classpath:/data/spring_ai_alibaba_quickstart.pdf")
//    private Resource springAiResource;
//
//
//    /**
//     * DashScope API 实例，用于调用 DashScope 提供的各种能力。
//     */
//    private final DashScopeApi dashscopeApi;
//
//    public KnowledgeBaseServiceImpl(DashScopeApi dashscopeApi) {
//        this.dashscopeApi = dashscopeApi;
//    }


//    /**
//     * 导入并处理文档资源，将其切分后存入 DashScope 云端向量存储中。
//     * 包括以下步骤：
//     * 1. 将资源文件保存为临时文件；
//     * 2. 读取并切分文档；
//     * 3. 存储到 DashScope 云向量库。
//     */
//    @Override
//    public void importDocuments() {
//        String path = saveToTempFile(springAiResource);
//
//        // 1. 导入并切分文档
//        DocumentReader reader = new DashScopeDocumentCloudReader(path, dashscopeApi, null);
//        List<Document> documentList = reader.get();
//        logger.info("{} documents loaded and split", documentList.size());
//
//        // 2. 将文档添加到 DashScope 云端向量存储
//        VectorStore vectorStore = new DashScopeCloudStore(dashscopeApi, new DashScopeStoreOptions(indexName));
//        vectorStore.add(documentList);
//        logger.info("{} documents added to dashscope cloud vector store", documentList.size());
//    }
//
//    /**
//     * 将给定的资源文件保存为临时文件，并返回其绝对路径。
//     *
//     * @param springAiResource 要保存的资源文件
//     * @return 临时文件的绝对路径
//     */
//    private String saveToTempFile(Resource springAiResource) {
//        try {
//            File tempFile = File.createTempFile("spring_ai_alibaba_quickstart", ".pdf");
//            tempFile.deleteOnExit();
//
//            try (InputStream inputStream = springAiResource.getInputStream();
//                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
//                byte[] buffer = new byte[4096];
//                int bytesRead;
//                while ((bytesRead = inputStream.read(buffer)) != -1) {
//                    outputStream.write(buffer, 0, bytesRead);
//                }
//            }
//
//            return tempFile.getAbsolutePath();
//        }
//        catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }


//    /**
//     * 文件上传接口
//     * @param file 上传文件
//     */
//    @Override
//    public void uploadDocument(MultipartFile file) {
//        try {
//            // 创建临时文件
//            File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
//            List<Document> documentList = getDocuments(file, tempFile);
//            logger.info("{} documents loaded and split from uploaded file", documentList.size());
//
//            // 将文档添加到 DashScope 云端向量存储
//            VectorStore vectorStore = new DashScopeCloudStore(dashscopeApi, new DashScopeStoreOptions(indexName));
//            vectorStore.add(documentList);
//            logger.info("{} documents added to dashscope cloud vector store from uploaded file", documentList.size());
//
//        } catch (IOException e) {
//            logger.error("Failed to upload and process document", e);
//            throw new RuntimeException("Failed to upload and process document", e);
//        }
//    }

    @Override
    public Long addKnowledgeBase(KnowledgeBaseDto knowledgeBaseDto) {
        if (StringUtils.isBlank(knowledgeBaseDto.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (UserStatusContext.getCurrentUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (knowledgeBaseDto.getUserId() != null && UserStatusContext.getCurrentUserId() != null && knowledgeBaseDto.getUserId().equals(UserStatusContext.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        knowledgeBaseDto.setUserId(UserStatusContext.getCurrentUserId());
        KnowledgeBase knowledgeBase = BeanUtil.copyProperties(knowledgeBaseDto, KnowledgeBase.class);
        boolean save = this.save(knowledgeBase);
        ThrowsUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR);
        return knowledgeBase.getId();
    }

    @Override
    public Page<KnowledgeBaseVo> pageKnowledgeBase(PageRequest pageRequest) {
        Long currentUserId = UserStatusContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Page<KnowledgeBase> knowledgeBasePage = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        knowledgeBasePage = this.page(knowledgeBasePage, new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getUserId, currentUserId));
        if (knowledgeBasePage.getRecords() == null || knowledgeBasePage.getRecords().isEmpty()) {
            return new Page<>();
        }
        List<KnowledgeBaseVo> knowledgeBaseVos = BeanUtil.copyToList(knowledgeBasePage.getRecords(), KnowledgeBaseVo.class);
        Page<KnowledgeBaseVo> knowledgeBaseVoPage = new Page<>();
        knowledgeBaseVoPage.setRecords(knowledgeBaseVos);
        knowledgeBaseVoPage.setTotal(knowledgeBasePage.getTotal());
        knowledgeBaseVoPage.setSize(knowledgeBasePage.getSize());
        knowledgeBaseVoPage.setCurrent(knowledgeBasePage.getCurrent());
        return knowledgeBaseVoPage;
    }

//    private List<Document> getDocuments(MultipartFile file, File tempFile) throws IOException {
//        tempFile.deleteOnExit();
//
//        // 将上传的文件内容写入临时文件
//        try (InputStream inputStream = file.getInputStream();
//             FileOutputStream outputStream = new FileOutputStream(tempFile)) {
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                outputStream.write(buffer, 0, bytesRead);
//            }
//        }
//
//        // 使用 DashScopeDocumentCloudReader 读取并切分文档
//        DocumentReader reader = new DashScopeDocumentCloudReader(tempFile.getAbsolutePath(), dashscopeApi, null);
//        List<Document> documentList = reader.get();
//        return documentList;
//    }

}

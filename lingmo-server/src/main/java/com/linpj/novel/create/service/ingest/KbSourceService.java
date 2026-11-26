package com.linpj.novel.create.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.pojo.entity.NovelKbSource;
import com.linpj.novel.create.mapper.novel.NovelKbSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.HexFormat;

/**
 * 素材来源管理（novel_kb_source 的唯一写入口）
 *
 * <p>同名文件重传 = 替换（uk_kb_source_file 语义）；删除走级联（chunk + 向量见 EmbeddingIndexService）。
 *
 * @author HL
 */
@Service
public class KbSourceService {

    private final NovelKbSourceMapper sourceMapper;
    private final EmbeddingIndexService embeddingIndexService;

    public KbSourceService(NovelKbSourceMapper sourceMapper, EmbeddingIndexService embeddingIndexService) {
        this.sourceMapper = sourceMapper;
        this.embeddingIndexService = embeddingIndexService;
    }

    /** 新建素材来源（MANUAL：手工文本；每次入库一条新来源） */
    public NovelKbSource createManual(Long bookId, Long userId, String content) {
        NovelKbSource source = new NovelKbSource();
        source.setBookId(bookId);
        source.setUserId(userId);
        source.setSourceKind("MANUAL");
        source.setName(nameOf(content));
        source.setDescription("手工录入素材");
        source.setChecksum(sha256(content));
        source.setStatus("PARSING");
        source.setCreatedAt(new Date());
        source.setUpdatedAt(new Date());
        sourceMapper.insert(source);
        return source;
    }

    /** 新建/复用文件来源（同名文件重传 = 替换旧来源内容） */
    public NovelKbSource upsertUpload(Long bookId, Long userId, String fileName, String filePath) {
        NovelKbSource existing = sourceMapper.selectOne(new LambdaQueryWrapper<NovelKbSource>()
                .eq(NovelKbSource::getBookId, bookId)
                .eq(NovelKbSource::getFileName, fileName)
                .last("limit 1"));
        if (existing != null) {
            existing.setFilePath(filePath);
            existing.setStatus("PARSING");
            existing.setUpdatedAt(new Date());
            sourceMapper.updateById(existing);
            return existing;
        }
        NovelKbSource source = new NovelKbSource();
        source.setBookId(bookId);
        source.setUserId(userId);
        source.setSourceKind("UPLOAD");
        source.setName(fileName);
        source.setFileName(fileName);
        source.setFilePath(filePath);
        source.setStatus("PARSING");
        source.setCreatedAt(new Date());
        source.setUpdatedAt(new Date());
        sourceMapper.insert(source);
        return source;
    }

    /** 素材解析完成后落状态（chunkCount=0 视为 FAILED） */
    public void markReady(NovelKbSource source, int chunkCount) {
        source.setStatus(chunkCount > 0 ? "READY" : "FAILED");
        source.setUpdatedAt(new Date());
        sourceMapper.updateById(source);
    }

    public NovelKbSource getById(Long id) {
        NovelKbSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "素材不存在");
        }
        return source;
    }

    public List<NovelKbSource> listByBook(Long bookId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<NovelKbSource>()
                .eq(NovelKbSource::getBookId, bookId)
                .orderByDesc(NovelKbSource::getId));
    }

    /** 删除素材：级联清 chunk 行 + 向量 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSource(Long sourceId) {
        NovelKbSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "素材不存在");
        }
        embeddingIndexService.deleteSource(sourceId);
        sourceMapper.deleteById(sourceId);
    }

    private static String nameOf(String content) {
        String firstLine = content == null ? "" : content.strip().split("\n", 2)[0].strip();
        String base = firstLine.isEmpty() ? "文本素材" : firstLine;
        return base.length() > 40 ? base.substring(0, 40) + "…" : base;
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null; // 校验和缺失不影响主流程
        }
    }
}

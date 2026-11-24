package com.linpj.novel.create.security;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelSafetyLogMapper;
import com.linpj.novel.create.pojo.entity.NovelSafetyLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 安全审计服务（v2.0 设计 7.1/7.4 / M4-E1）
 *
 * - 三要素落账：谁（bookId）/ 因何（layer+category）/ 如何处置（action）+ detail
 * - detail 转义留证：控制字符剥离 + HTML 敏感符号转义 + 长度截断——
 *   审计日志本身要防注入（红线 2 延伸：日志查看端不是二次攻击面）
 * - 审计是观测能力：任何写入失败只告警不阻塞主链路（红线：观测不反噬可用性）
 *
 * @author HL
 */
@Service
public class SafetyAuditService extends ServiceImpl<NovelSafetyLogMapper, NovelSafetyLog> {

    private static final Logger log = LoggerFactory.getLogger(SafetyAuditService.class);

    /** 留证长度上限（超长截断；操纵载荷没有必要完整入库） */
    static final int MAX_DETAIL_LENGTH = 500;

    public void record(Long bookId, String layer, String category, String action, String rawDetail) {
        if (layer == null || category == null || action == null) {
            return;
        }
        try {
            NovelSafetyLog entry = new NovelSafetyLog();
            entry.setBookId(bookId);
            entry.setLayer(layer);
            entry.setCategory(category);
            entry.setAction(action);
            entry.setDetail(escape(rawDetail));
            entry.setCreatedAt(new Date());
            this.save(entry);
        } catch (Exception e) {
            // 审计失败不阻塞业务主链路（P6 只约束安全校验本体，不约束记账通道）
            log.error("安全审计写入失败（业务继续）: layer={}, category={}, action={}", layer, category, action, e);
        }
    }

    /**
     * 留证转义：控制字符剥离（含换行，防日志注入伪造行）+ HTML 敏感符号转义 + 截断。
     */
    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}\\r\\n\\t]", " ");
        cleaned = cleaned.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        if (cleaned.length() > MAX_DETAIL_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DETAIL_LENGTH) + "…(truncated)";
        }
        return cleaned;
    }
}

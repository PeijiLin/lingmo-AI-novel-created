package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.worldstate.CostLedgerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成本账本入口（M5-F4：论文 §10.7 性能成本实验 / §11.1 相对成本模型的导出口径）
 *
 * GET /api/book/cost/{bookId}/summary   JSON 汇总（模式 × 节点 × 模型 分组）
 * GET /api/book/cost/{bookId}/export    CSV 导出（Excel 兼容：UTF-8 BOM + 逗号分隔）
 *
 * CSV 列与融合稿 §11.1 成本表对齐（节点级 token/成本；未计价 token 单列——真实不虚增）。
 *
 * @author HL
 */
@RestController
@RequestMapping("/api/book/cost")
public class CostLedgerController {

    private final CostLedgerService costLedgerService;

    public CostLedgerController(CostLedgerService costLedgerService) {
        this.costLedgerService = costLedgerService;
    }

    /** JSON 汇总（前端成本面板 / 快速核对） */
    @GetMapping("/{bookId}/summary")
    public BaseResponse<List<CostLedgerService.CostRow>> summary(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(costLedgerService.summarizeByBook(bookId));
    }

    /** CSV 导出（Excel 直接打开；未计价（价目缺配）行在 priced 列标 0） */
    @GetMapping("/{bookId}/export")
    public ResponseEntity<byte[]> export(@PathVariable("bookId") Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR);
        List<CostLedgerService.CostRow> rows = costLedgerService.summarizeByBook(bookId);
        StringBuilder csv = new StringBuilder();
        csv.append("模式,节点,模型,运行次数,tokens,成本(元),已计价\n");
        for (CostLedgerService.CostRow row : rows) {
            csv.append(csv(row.mode())).append(',')
                    .append(csv(row.node())).append(',')
                    .append(csv(row.model())).append(',')
                    .append(row.runs()).append(',')
                    .append(row.totalTokens()).append(',')
                    .append(row.cost() == null ? BigDecimal.ZERO : row.cost()).append(',')
                    .append(row.priced() ? 1 : 0).append('\n');
        }
        byte[] body = ('\uFEFF' + csv.toString()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=cost-" + bookId + ".csv")
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .body(body);
    }

    /** CSV 字段转义（含逗号/引号/换行的字段包引号翻倍） */
    private static String csv(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}

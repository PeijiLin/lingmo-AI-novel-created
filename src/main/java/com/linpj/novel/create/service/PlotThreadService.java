package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;

import java.util.List;

/**
 * 伏笔账本服务（核心资产）
 *
 * 准入规则（设计 5.3）：plot_threads 的 PLANT/RESOLVE 以 Planner 声明 + Critic 复核为准入
 * （写作意图先入账，防止"写了但没人记账"），正文内联 <!--plot:--> 标记只做补充。
 *
 * @author HL
 */
public interface PlotThreadService extends IService<NovelPlotThread> {

    /** 列出本书所有活跃（OPEN）伏笔 */
    List<NovelPlotThread> listActiveThreads(Long bookId);

    /** 渲染活跃伏笔清单为 prompt 文本 */
    String renderActiveThreads(Long bookId);

    /**
     * 应用 Planner 声明的伏笔操作（Critic 复核通过后调用）
     * PLANT：threadId 为空则按 description 新埋（幂等：同描述已存在则跳过）
     * RESOLVE：按 threadId 收束（状态置 RESOLVED + 记录收束章节）
     * ADVANCE：推进记账（更新时间戳，描述保持）
     */
    void applyPlotOperations(Long bookId, Long chapterId, List<PlotOperation> plotOps);

    /** 从正文提取内联伏笔标记 <!--plot: 描述--> */
    List<String> extractInlinePlotMarks(String text);

    /** 剥离正文中的内联伏笔标记，返回干净正文 */
    String stripInlinePlotMarks(String text);

    /** 内联标记入账（新增 OPEN 伏笔；幂等：同描述已存在则跳过） */
    void recordInlinePlotMarks(Long bookId, Long chapterId, List<String> descriptions);
}

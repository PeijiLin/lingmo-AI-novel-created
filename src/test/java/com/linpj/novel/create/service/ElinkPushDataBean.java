package com.linpj.novel.create.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class ElinkPushDataBean {

    /** 变电站名称 → {地点名称} */
    private String substationName;

    /** 设备名称 → {设备} */
    private String deviceName;

    /** 设备相别 → {相别} */
    private String phase;

    /** 项目名称（色谱/微水/耐压/介损/含气量） → {色谱/微水/耐压/介损/含气量} */
    private String projectName;

    /** 上次试验时间 → {项目上次工作时间} */
    private String testTime;

    /** 在线监测时间 → {在线监测时间}（仅在线相别预警用） */
    private String monitorTime;

    /**
     * 异常情况 → {异常情况}
     * <p>
     * 由调用方预渲染为完整描述，格式示例：
     * "微水本次试验值为：15.2"，触发"微水超标"
     */
    private String abnormalDetail;

    /** 执行周期 → {执行周期}（仅临期提醒用，如 "6个月"） */
    private String cyclePeriod;

    /** 下次预试计划时间 → {下次预试计划时间}（仅临期提醒用） */
    private String dueTime;
}

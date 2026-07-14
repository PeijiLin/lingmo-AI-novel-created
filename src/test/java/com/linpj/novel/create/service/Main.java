package com.linpj.novel.create.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        List<ElinkPushDataBean> dataList = Arrays.asList(
                createTestDataBean("500kV变电站", "#1主变", "A相", "色谱", "2026-06-01 10:00:00", "氢气含量超标"),
                createTestDataBean("220kV变电站", "#2主变", "B相", "微水", "2026-06-02 14:30:00", "微水含量偏高"),
                createTestDataBean("110kV变电站", "#3主变", "C相", "耐压", "2026-06-03 09:15:00", "耐压试验不合格")
        );
        pushToElinkInfo(dataList);

    }


    /**
     * 创建测试数据Bean
     */
    private static ElinkPushDataBean createTestDataBean(String substation, String device, String phase,
                                                 String project, String testTime, String abnormal) {
        ElinkPushDataBean bean = new ElinkPushDataBean();
        bean.setSubstationName(substation);
        bean.setDeviceName(device);
        bean.setPhase(phase);
        bean.setProjectName(project);
        bean.setTestTime(testTime);
        bean.setMonitorTime("");
        bean.setAbnormalDetail(abnormal);
        bean.setCyclePeriod("");
        bean.setDueTime("");
        return bean;
    }
    /**
     * 将BigDecimal转换为科学计数法字符串（如 5×10⁹）
     * @param value BigDecimal值
     * @return 科学计数法字符串，如果为null则返回空字符串
     */
    private static String toScientificNotation(BigDecimal value) {
        if (value == null) {
            return "";
        }

        BigDecimal stripped = value.stripTrailingZeros();
        String plainString = stripped.toPlainString();

        // 如果数值较小，直接返回普通格式
        if (stripped.abs().compareTo(new BigDecimal("1000")) < 0 &&
                stripped.abs().compareTo(new BigDecimal("0.001")) >= 0) {
            return plainString;
        }

        // 转换为科学计数法
        String scientific = stripped.toString();
        if (!scientific.contains("E") && !scientific.contains("e")) {
            return plainString;
        }

        // 解析系数和指数
        String[] parts = scientific.split("[Ee]");
        String coefficient = parts[0];
        int exponent = Integer.parseInt(parts[1]);

        // 将指数转换为上标
        String superscriptExponent = toSuperscript(exponent);

        // 格式化：系数 × 10^指数
        return coefficient + "×10" + superscriptExponent;
    }

    /**
     * 将整数转换为上标字符
     */
    private static String toSuperscript(int number) {
        String numStr = String.valueOf(number);
        StringBuilder result = new StringBuilder();

        for (char c : numStr.toCharArray()) {
            switch (c) {
                case '-':
                    result.append('⁻');
                    break;
                case '0':
                    result.append('⁰');
                    break;
                case '1':
                    result.append('¹');
                    break;
                case '2':
                    result.append('²');
                    break;
                case '3':
                    result.append('³');
                    break;
                case '4':
                    result.append('⁴');
                    break;
                case '5':
                    result.append('⁵');
                    break;
                case '6':
                    result.append('⁶');
                    break;
                case '7':
                    result.append('⁷');
                    break;
                case '8':
                    result.append('⁸');
                    break;
                case '9':
                    result.append('⁹');
                    break;
                default:
                    result.append(c);
            }
        }

        return result.toString();
    }
        public static void pushToElinkInfo(List<ElinkPushDataBean> elinkdataList) {
        StringBuilder templateContents = new StringBuilder();
        int index = 1;
            String oneTemplateContent = "【离线数据相别规范性预警】\n" +
                    " 以下为待确认异常试验数据：\n" +
                    "（1）变电站：{地点名称}；\n" +
                    "      设备：{设备}；\n" +
                    "      相别：{相别}；\n" +
                    "      试验时间：{项目上次工作时间}；\n" +
                    "      异常情况：{异常情况}，请确认该相别命名是否符合规范。";
        for (ElinkPushDataBean elinkPushDataBean : elinkdataList) {
            Map<String, String> FIELD_PLACEHOLDER_MAP = new LinkedHashMap<>();
            FIELD_PLACEHOLDER_MAP.put("{地点名称}", elinkPushDataBean.getSubstationName());
            FIELD_PLACEHOLDER_MAP.put("{设备}", elinkPushDataBean.getDeviceName());
            FIELD_PLACEHOLDER_MAP.put("{相别}", elinkPushDataBean.getPhase());
            FIELD_PLACEHOLDER_MAP.put("{色谱/微水/耐压/介损/含气量}", elinkPushDataBean.getProjectName());
            FIELD_PLACEHOLDER_MAP.put("{项目上次工作时间}", elinkPushDataBean.getTestTime());
            FIELD_PLACEHOLDER_MAP.put("{在线检测时间}", elinkPushDataBean.getMonitorTime());
            FIELD_PLACEHOLDER_MAP.put("{异常情况}", elinkPushDataBean.getAbnormalDetail());
            FIELD_PLACEHOLDER_MAP.put("{执行周期}", elinkPushDataBean.getCyclePeriod());
            FIELD_PLACEHOLDER_MAP.put("{下次预试计划时间}", elinkPushDataBean.getDueTime());


            for (String s : FIELD_PLACEHOLDER_MAP.keySet()) {
                if (oneTemplateContent.contains(s)) {
                    oneTemplateContent = oneTemplateContent.replace(s, FIELD_PLACEHOLDER_MAP.get(s));
                }
            }

            if (index == 1) {
                templateContents.append(oneTemplateContent);
            } else {
                templateContents.append("\n(").append(index).append(")").append(oneTemplateContent.substring(oneTemplateContent.indexOf("变电站：")));
            }
            index++;
        }
        System.out.println(templateContents);
    }

}

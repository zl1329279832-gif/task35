package com.coderman.common.vo.business;

import lombok.Data;

/**
 * 风险概览VO
 */
@Data
public class RiskDashboardVO {

    /** 正常物资数量 */
    private int normalCount;

    /** 低风险物资数量 */
    private int lowCount;

    /** 中风险物资数量 */
    private int mediumCount;

    /** 高风险物资数量 */
    private int highCount;

    /** 紧急物资数量 */
    private int criticalCount;

    /** 总物资数量 */
    private int totalCount;

    /** 待处理建议数 */
    private int pendingSuggestionCount;
}

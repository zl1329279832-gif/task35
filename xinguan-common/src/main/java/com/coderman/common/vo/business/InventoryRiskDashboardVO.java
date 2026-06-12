package com.coderman.common.vo.business;

import lombok.Data;

import java.util.List;

/**
 * 库存风险仪表盘VO
 */
@Data
public class InventoryRiskDashboardVO {
    private Integer totalProducts;
    private Integer criticalCount;
    private Integer highCount;
    private Integer mediumCount;
    private Integer lowCount;
    private Integer safeCount;
    private Integer expiryRiskCount;
    private Integer pendingSuggestionCount;
    private List<InventoryRiskSnapshotVO> riskItems;
}

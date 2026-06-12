package com.coderman.common.vo.business;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存风险快照VO
 */
@Data
public class StockRiskSnapshotVO {

    private Long id;

    private String snapshotNum;

    private String pNum;

    private String productName;

    private Long totalStock;

    private Long availableStock;

    private Long lockedStock;

    private Long inTransitStock;

    private Long nearExpiryStock;

    private BigDecimal avgDailyOutbound;

    private BigDecimal availableDays;

    private Long gapQuantity;

    private Integer riskLevel;

    private String riskLevelDesc;

    private Integer ruleVersion;

    private Date createTime;
}

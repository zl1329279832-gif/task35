package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存风险快照VO
 */
@Data
public class InventoryRiskSnapshotVO {
    private Long id;
    private String pNum;
    private String productName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date snapshotTime;

    private Long totalBatchQuantity;
    private Long lockedQuantity;
    private Long inTransitQuantity;
    private Long availableStock;
    private BigDecimal dailyConsumptionRate;
    private Integer lookbackDays;
    private BigDecimal availableDays;
    private Integer nearExpiryBatchCount;
    private Long nearExpiryQuantity;
    private String riskLevel;
    private Integer hasExpiryRisk;
    private Long ruleVersionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 近效期批次详情(详情查询时填充) */
    private List<ProductBatchVO> nearExpiryBatches;
}

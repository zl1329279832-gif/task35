package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 物资批次VO
 */
@Data
public class ProductBatchVO {

    private Long id;

    private String pNum;

    private String productName;

    private String batchNum;

    private Long supplierId;

    private String supplierName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date productionDate;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date expiryDate;

    /** 质检状态: 0-待检, 1-合格, 2-不合格 */
    private Integer inspectionStatus;

    /** 储备等级: 1-普通, 2-重要, 3-紧急 */
    private Integer reserveLevel;

    private Long batchStock;

    private Long lockedStock;

    /** 可用库存 = batchStock - lockedStock */
    private Long availableStock;

    private String inNum;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String remark;

    /** 剩余有效天数 */
    private Long remainingDays;
}

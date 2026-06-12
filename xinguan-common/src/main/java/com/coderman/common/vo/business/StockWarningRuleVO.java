package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 库存预警规则VO
 */
@Data
public class StockWarningRuleVO {

    private Long id;

    private String pNum;

    private String productName;

    private Integer minStockDays;

    private Integer nearExpiryDays;

    private Long safetyStock;

    private Long reorderPoint;

    private Integer version;

    private Integer status;

    private Date createTime;

    private Date modifiedTime;
}

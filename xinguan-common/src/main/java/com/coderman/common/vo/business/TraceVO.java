package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 批次追溯VO
 */
@Data
public class TraceVO {

    /** 批次号 */
    private String batchNum;

    /** 物资编号 */
    private String pNum;

    /** 物资名称 */
    private String productName;

    /** 供应商名称 */
    private String supplierName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date productionDate;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date expiryDate;

    /** 质检状态 */
    private Integer inspectionStatus;

    /** 储备等级 */
    private Integer reserveLevel;

    /** 当前批次库存 */
    private Long batchStock;

    /** 入库单号 */
    private String inNum;

    /** 入库时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date inStockTime;

    /** 入库操作人 */
    private String inStockOperator;

    /** 关联出库单号(逗号分隔) */
    private String outNums;

    /** 关联调拨单号(逗号分隔) */
    private String transferNums;
}

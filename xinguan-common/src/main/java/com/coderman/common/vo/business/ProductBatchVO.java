package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 物资批次VO
 */
@Data
public class ProductBatchVO {

    private Long id;
    private String batchNumber;
    private String pNum;
    private String productName;
    private String inNum;
    private Long supplierId;
    private String supplierName;
    private Date productionDate;
    private Date expiryDate;
    private Integer qualityStatus;
    private Integer reserveLevel;
    private Long quantity;
    private Long lockedQuantity;
    private Long availableQuantity;
    private Integer status;
    private Date createTime;

    /** 追溯事件链（仅追溯查询时填充） */
    private List<BatchTraceEventVO> traceEvents;
}

package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 调拨明细VO
 */
@Data
public class TransferRequestInfoVO {

    private Long id;
    private String transferNum;
    private String batchNumber;
    private Long allocatedQuantity;
    private Long confirmedQuantity;
    private String productName;
    private Date productionDate;
    private Date expiryDate;
    private Integer qualityStatus;
}

package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 批次分配项VO
 */
@Data
public class BatchAllocationItemVO {

    private String batchNumber;
    private Long allocatedQuantity;
    private Date productionDate;
    private Date expiryDate;
    private Integer reserveLevel;
    private String supplierName;
}

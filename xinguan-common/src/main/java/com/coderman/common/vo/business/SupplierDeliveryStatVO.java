package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 供应商交付统计VO
 */
@Data
public class SupplierDeliveryStatVO {
    private Long id;
    private Long supplierId;
    private String supplierName;
    private String pNum;
    private String productName;
    private Integer totalDeliveries;
    private Integer onTimeDeliveries;
    private BigDecimal avgLeadDays;
    private Integer maxLeadDays;
    private Integer minLeadDays;
    private BigDecimal onTimeRate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastDeliveryTime;

    private Integer promisedLeadDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date modifiedTime;
}

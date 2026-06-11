package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Data
@Table(name = "biz_in_stock_info")
public class InStockInfo {

    @Id
    private Long id;

    private String inNum;

    private String pNum;

    private Integer productNumber;

    private Date createTime;

    private Date modifiedTime;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "production_date")
    private Date productionDate;

    @Column(name = "expiry_date")
    private Date expiryDate;

    @Column(name = "quality_status")
    private Integer qualityStatus;

    @Column(name = "reserve_level")
    private Integer reserveLevel;

}

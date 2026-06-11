package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 物资批次实体
 */
@Data
@Table(name = "biz_product_batch")
public class ProductBatch {

    @Id
    private Long id;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "in_num")
    private String inNum;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "production_date")
    private Date productionDate;

    @Column(name = "expiry_date")
    private Date expiryDate;

    @Column(name = "quality_status")
    private Integer qualityStatus;

    @Column(name = "reserve_level")
    private Integer reserveLevel;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "locked_quantity")
    private Long lockedQuantity;

    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}

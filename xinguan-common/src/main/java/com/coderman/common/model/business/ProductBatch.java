package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 物资批次信息
 */
@Data
@Table(name = "biz_product_batch")
public class ProductBatch {

    @Id
    private Long id;

    /** 物资编号 */
    private String pNum;

    /** 批次号 */
    private String batchNum;

    /** 供应商ID */
    private Long supplierId;

    /** 生产日期 */
    private Date productionDate;

    /** 有效期 */
    private Date expiryDate;

    /** 质检状态: 0-待检, 1-合格, 2-不合格 */
    private Integer inspectionStatus;

    /** 储备等级: 1-普通, 2-重要, 3-紧急 */
    private Integer reserveLevel;

    /** 批次库存数量 */
    private Long batchStock;

    /** 锁定库存数量(调拨冻结) */
    private Long lockedStock;

    /** 入库单号 */
    private String inNum;

    /** 创建时间 */
    private Date createTime;

    /** 修改时间 */
    private Date modifiedTime;

    /** 备注 */
    private String remark;
}

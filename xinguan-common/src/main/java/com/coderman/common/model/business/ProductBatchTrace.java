package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 物资批次追溯事件实体
 * 记录批次从入库、质检、锁定、出库、接收到异常回滚的全链路事件
 */
@Data
@Table(name = "biz_product_batch_trace")
public class ProductBatchTrace {

    @Id
    @GeneratedValue(generator = "JDBC")
    private Long id;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "p_num")
    private String pNum;

    /**
     * 事件类型: IN(入库) / QC(质检) / LOCK(锁定) / UNLOCK(解锁) / OUT(出库) / RECEIVE(接收) / ROLLBACK(回滚)
     */
    @Column(name = "event_type")
    private String eventType;

    @Column(name = "quantity")
    private Long quantity;

    /**
     * 关联单号(入库单/调拨单/出库单)
     */
    @Column(name = "ref_num")
    private String refNum;

    @Column(name = "operator")
    private String operator;

    @Column(name = "remark")
    private String remark;

    @Column(name = "create_time")
    private Date createTime;
}

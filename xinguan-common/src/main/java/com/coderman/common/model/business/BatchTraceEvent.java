package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 批次追溯事件实体
 * 记录批次从入库、质检、锁定、出库、接收到异常回滚的全链路
 */
@Data
@Table(name = "biz_batch_trace_event")
public class BatchTraceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 批次号 */
    @Column(name = "batch_number")
    private String batchNumber;

    /** 物资编号 */
    @Column(name = "p_num")
    private String pNum;

    /**
     * 事件类型:
     * IN_STOCK      - 入库创建
     * QUALITY_PASS  - 质检合格
     * QUALITY_FAIL  - 质检不合格
     * LOCKED        - 库存锁定(调拨审批/出库审核)
     * UNLOCKED      - 锁定释放(审批拒绝/回滚)
     * DEDUCTED      - 确认扣减(出库/发送)
     * TRANSFER_IN   - 调拨接收(目标端新批次)
     * ROLLBACK      - 异常回滚(恢复库存)
     * EXPIRED       - 过期标记
     */
    @Column(name = "event_type")
    private String eventType;

    /** 关联业务单号(入库单号/出库单号/调拨单号) */
    @Column(name = "related_num")
    private String relatedNum;

    /** 操作数量 */
    @Column(name = "quantity")
    private Long quantity;

    /** 操作前批次数量快照 */
    @Column(name = "before_quantity")
    private Long beforeQuantity;

    /** 操作前锁定数量快照 */
    @Column(name = "before_locked_quantity")
    private Long beforeLockedQuantity;

    /** 操作后批次数量快照 */
    @Column(name = "after_quantity")
    private Long afterQuantity;

    /** 操作后锁定数量快照 */
    @Column(name = "after_locked_quantity")
    private Long afterLockedQuantity;

    /** 操作人 */
    @Column(name = "operator")
    private String operator;

    /** 备注/原因 */
    @Column(name = "remark")
    private String remark;

    /** 事件时间 */
    @Column(name = "event_time")
    private Date eventTime;
}

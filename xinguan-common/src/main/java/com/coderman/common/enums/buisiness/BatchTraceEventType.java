package com.coderman.common.enums.buisiness;

/**
 * 批次追溯事件类型常量
 */
public final class BatchTraceEventType {

    private BatchTraceEventType() {}

    /** 入库创建 */
    public static final String IN_STOCK = "IN_STOCK";

    /** 质检合格 */
    public static final String QUALITY_PASS = "QUALITY_PASS";

    /** 质检不合格 */
    public static final String QUALITY_FAIL = "QUALITY_FAIL";

    /** 库存锁定(调拨审批/出库审核) */
    public static final String LOCKED = "LOCKED";

    /** 锁定释放(审批拒绝/回滚) */
    public static final String UNLOCKED = "UNLOCKED";

    /** 确认扣减(出库/发送) */
    public static final String DEDUCTED = "DEDUCTED";

    /** 调拨接收(目标端新批次) */
    public static final String TRANSFER_IN = "TRANSFER_IN";

    /** 异常回滚(恢复库存) */
    public static final String ROLLBACK = "ROLLBACK";

    /** 过期标记 */
    public static final String EXPIRED = "EXPIRED";
}

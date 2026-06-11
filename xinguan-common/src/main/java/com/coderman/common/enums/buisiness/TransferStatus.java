package com.coderman.common.enums.buisiness;

/**
 * 调拨单状态常量
 */
public final class TransferStatus {

    private TransferStatus() {}

    /** 完成 */
    public static final int COMPLETED = 0;
    /** 拒绝 */
    public static final int REJECTED = 1;
    /** 待审批 */
    public static final int PENDING = 2;
    /** 已审批 */
    public static final int APPROVED = 3;
    /** 已发送 */
    public static final int SENT = 4;
    /** 已接收(中间态,代码中直接跳到COMPLETED) */
    public static final int RECEIVED = 5;
    /** 回滚 */
    public static final int ROLLED_BACK = 6;
}

package com.coderman.common.enums.buisiness;

/**
 * 批次质检状态常量
 */
public final class QualityStatus {

    private QualityStatus() {}

    /** 待检 */
    public static final int PENDING_INSPECTION = 0;
    /** 合格 */
    public static final int QUALIFIED = 1;
    /** 不合格 */
    public static final int UNQUALIFIED = 2;
    /** 过期 */
    public static final int EXPIRED = 3;
}

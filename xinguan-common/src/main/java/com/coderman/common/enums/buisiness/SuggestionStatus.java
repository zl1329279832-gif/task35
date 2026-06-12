package com.coderman.common.enums.buisiness;

/**
 * 建议单状态常量
 */
public final class SuggestionStatus {

    private SuggestionStatus() {}

    /** 待处理 */
    public static final int PENDING = 0;
    /** 已采纳 */
    public static final int ADOPTED = 1;
    /** 已驳回 */
    public static final int REJECTED = 2;
    /** 已过期 */
    public static final int EXPIRED = 3;
}

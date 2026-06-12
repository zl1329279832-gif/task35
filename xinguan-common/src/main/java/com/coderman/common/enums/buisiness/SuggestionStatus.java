package com.coderman.common.enums.buisiness;

/**
 * 补货建议状态常量
 */
public final class SuggestionStatus {
    private SuggestionStatus() {}

    public static final int PENDING = 0;
    public static final int ADOPTED = 1;
    public static final int REJECTED = 2;
    public static final int EXECUTED = 3;
    public static final int EXPIRED = 4;
}

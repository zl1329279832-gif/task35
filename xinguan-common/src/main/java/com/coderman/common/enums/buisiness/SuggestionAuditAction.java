package com.coderman.common.enums.buisiness;

/**
 * 建议审计操作类型常量
 */
public final class SuggestionAuditAction {
    private SuggestionAuditAction() {}

    public static final String CREATED = "CREATED";
    public static final String ADOPTED = "ADOPTED";
    public static final String REJECTED = "REJECTED";
    public static final String EXECUTED = "EXECUTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String TRANSFER_REJECTED = "TRANSFER_REJECTED";
}

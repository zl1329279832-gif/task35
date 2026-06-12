package com.coderman.business.event;

/**
 * 入库审批通过事件
 * 在入库审批完成后发布，用于更新供应商交付统计
 */
public class InStockApprovedEvent {

    private final Long supplierId;
    private final String pNum;
    private final long quantity;

    public InStockApprovedEvent(Long supplierId, String pNum, long quantity) {
        this.supplierId = supplierId;
        this.pNum = pNum;
        this.quantity = quantity;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getPNum() {
        return pNum;
    }

    public long getQuantity() {
        return quantity;
    }
}

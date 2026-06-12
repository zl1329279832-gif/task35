package com.coderman.business.event;

/**
 * 库存变动事件
 * 在出库、入库、调拨等操作完成后发布，触发风险重算
 */
public class StockChangedEvent {

    private final String pNum;

    public StockChangedEvent(String pNum) {
        this.pNum = pNum;
    }

    public String getPNum() {
        return pNum;
    }
}

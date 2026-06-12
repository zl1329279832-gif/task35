package com.coderman.business.event;

import com.coderman.business.service.StockWarningService;
import com.coderman.business.service.SupplierDeliveryStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 库存预警事件监听器
 * 监听库存变动事件，异步触发风险重算和供应商统计更新
 */
@Component
public class StockWarningEventListener {

    private static final Logger log = LoggerFactory.getLogger(StockWarningEventListener.class);

    @Autowired(required = false)
    private StockWarningService stockWarningService;

    @Autowired(required = false)
    private SupplierDeliveryStatsService supplierDeliveryStatsService;

    /**
     * 监听库存变动事件 → 触发风险重算
     */
    @EventListener
    public void onStockChanged(StockChangedEvent event) {
        if (stockWarningService == null) {
            return;
        }
        try {
            log.info("收到库存变动事件, pNum={}, 触发风险重算", event.getPNum());
            stockWarningService.recalculateRisk(event.getPNum());
        } catch (Exception e) {
            log.error("风险重算失败, pNum={}", event.getPNum(), e);
        }
    }

    /**
     * 监听入库审批事件 → 更新供应商交付统计
     */
    @EventListener
    public void onInStockApproved(InStockApprovedEvent event) {
        if (supplierDeliveryStatsService == null) {
            return;
        }
        try {
            log.info("收到入库审批事件, supplierId={}, pNum={}, quantity={}",
                    event.getSupplierId(), event.getPNum(), event.getQuantity());
            supplierDeliveryStatsService.updateStatsOnInStock(
                    event.getSupplierId(), event.getPNum(), 0, 0);
        } catch (Exception e) {
            log.error("供应商交付统计更新失败, supplierId={}, pNum={}",
                    event.getSupplierId(), event.getPNum(), e);
        }
    }
}

package com.coderman.config;

import com.coderman.business.service.InventoryRiskService;
import com.coderman.business.service.ReplenishSuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存风险定时任务
 */
@Component
@Slf4j
public class InventoryRiskScheduler {

    @Autowired
    private InventoryRiskService inventoryRiskService;

    @Autowired
    private ReplenishSuggestionService replenishSuggestionService;

    /**
     * 每4小时计算风险快照
     */
    @Scheduled(cron = "0 0 */4 * * ?")
    public void calculateRiskSnapshots() {
        try {
            log.info("开始定时计算库存风险快照...");
            inventoryRiskService.calculateAllRiskSnapshots();
            log.info("库存风险快照计算完成");
        } catch (Exception e) {
            log.error("库存风险快照计算异常", e);
        }
    }

    /**
     * 每4小时(偏移10分钟)生成补货建议
     */
    @Scheduled(cron = "0 10 */4 * * ?")
    public void generateSuggestions() {
        try {
            log.info("开始定时生成补货建议...");
            replenishSuggestionService.generateSuggestions();
            log.info("补货建议生成完成");
        } catch (Exception e) {
            log.error("补货建议生成异常", e);
        }
    }

    /**
     * 每天凌晨2点过期处理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void expireStaleSuggestions() {
        try {
            log.info("开始过期建议清理...");
            replenishSuggestionService.expireStaleSuggestions();
            log.info("过期建议清理完成");
        } catch (Exception e) {
            log.error("过期建议清理异常", e);
        }
    }
}

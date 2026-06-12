-- ============================================================
-- 库存预警与跨仓补货建议 - DDL
-- ============================================================

-- 1. 预警规则表（版本化）
CREATE TABLE IF NOT EXISTS `biz_stock_warning_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `p_num` VARCHAR(64) DEFAULT NULL COMMENT '物资编号，NULL表示全局默认规则',
    `min_stock_days` INT NOT NULL DEFAULT 14 COMMENT '最低可用天数阈值',
    `near_expiry_days` INT NOT NULL DEFAULT 30 COMMENT '近效期预警天数',
    `safety_stock` BIGINT NOT NULL DEFAULT 100 COMMENT '安全库存量',
    `reorder_point` BIGINT NOT NULL DEFAULT 50 COMMENT '补货触发点',
    `version` INT NOT NULL DEFAULT 1 COMMENT '规则版本号',
    `status` INT NOT NULL DEFAULT 0 COMMENT '状态: 0=活跃, 1=历史',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_p_num` (`p_num`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警规则';

-- 2. 库存风险快照表
CREATE TABLE IF NOT EXISTS `biz_stock_risk_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `snapshot_num` VARCHAR(64) NOT NULL COMMENT '快照唯一编号',
    `p_num` VARCHAR(64) NOT NULL COMMENT '物资编号',
    `total_stock` BIGINT NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_stock` BIGINT NOT NULL DEFAULT 0 COMMENT '可用库存(扣除锁定)',
    `locked_stock` BIGINT NOT NULL DEFAULT 0 COMMENT '已锁定库存',
    `in_transit_stock` BIGINT NOT NULL DEFAULT 0 COMMENT '在途调拨库存',
    `near_expiry_stock` BIGINT NOT NULL DEFAULT 0 COMMENT '近效期库存量',
    `avg_daily_outbound` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '日均出库速度',
    `available_days` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '可用天数',
    `gap_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '缺口量',
    `risk_level` INT NOT NULL DEFAULT 0 COMMENT '风险等级: 0=正常,1=低,2=中,3=高,4=紧急',
    `rule_version` INT NOT NULL DEFAULT 1 COMMENT '使用的规则版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snapshot_num` (`snapshot_num`),
    KEY `idx_p_num` (`p_num`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存风险快照';

-- 3. 补货/调拨建议单表
CREATE TABLE IF NOT EXISTS `biz_replenishment_suggestion` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `suggestion_num` VARCHAR(64) NOT NULL COMMENT '建议单唯一编号',
    `snapshot_id` BIGINT NOT NULL COMMENT '关联的风险快照ID',
    `p_num` VARCHAR(64) NOT NULL COMMENT '物资编号',
    `suggestion_type` INT NOT NULL COMMENT '建议类型: 1=采购补货, 2=跨仓调拨',
    `suggested_quantity` BIGINT NOT NULL DEFAULT 0 COMMENT '建议数量',
    `source_department` VARCHAR(128) DEFAULT NULL COMMENT '调拨来源仓库(补货时为NULL)',
    `target_department` VARCHAR(128) DEFAULT NULL COMMENT '目标仓库',
    `supplier_id` BIGINT DEFAULT NULL COMMENT '推荐供应商ID(补货时)',
    `expected_delivery_days` INT DEFAULT NULL COMMENT '预计交付天数',
    `priority` INT NOT NULL DEFAULT 3 COMMENT '优先级: 1=紧急,2=高,3=中,4=低',
    `status` INT NOT NULL DEFAULT 0 COMMENT '状态: 0=待处理,1=已采纳,2=已驳回,3=已过期',
    `rule_version` INT NOT NULL DEFAULT 1 COMMENT '使用的规则版本号',
    `idempotent_key` VARCHAR(256) NOT NULL COMMENT '幂等键(pNum+snapshotNum+type)',
    `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    `remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_suggestion_num` (`suggestion_num`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_snapshot_id` (`snapshot_id`),
    KEY `idx_p_num` (`p_num`),
    KEY `idx_status` (`status`),
    KEY `idx_suggestion_type` (`suggestion_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货/调拨建议单';

-- 4. 供应商交付统计表
CREATE TABLE IF NOT EXISTS `biz_supplier_delivery_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `p_num` VARCHAR(64) NOT NULL COMMENT '物资编号',
    `total_orders` INT NOT NULL DEFAULT 0 COMMENT '总订单数',
    `on_time_orders` INT NOT NULL DEFAULT 0 COMMENT '准时交付数',
    `late_orders` INT NOT NULL DEFAULT 0 COMMENT '延迟交付数',
    `avg_delivery_days` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平均交付天数',
    `avg_delay_days` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平均延迟天数',
    `last_delivery_date` DATETIME DEFAULT NULL COMMENT '最近交付日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_product` (`supplier_id`, `p_num`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_p_num` (`p_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商交付统计';

-- 5. 建议采纳审计表
CREATE TABLE IF NOT EXISTS `biz_suggestion_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `suggestion_id` BIGINT NOT NULL COMMENT '关联建议单ID',
    `action` VARCHAR(32) NOT NULL COMMENT '操作: ADOPT/REJECT/EXPIRE',
    `operator` VARCHAR(64) NOT NULL COMMENT '操作人',
    `reason` VARCHAR(512) DEFAULT NULL COMMENT '操作原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_suggestion_id` (`suggestion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建议采纳审计';

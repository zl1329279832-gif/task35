-- =====================================================
-- 库存预警与跨仓补货建议 - 增量DDL
-- =====================================================

-- 1. 补货规则版本表
CREATE TABLE IF NOT EXISTS `biz_replenish_rule_version` (
    `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `version_num`                VARCHAR(32)  NOT NULL COMMENT '版本号,如V1.0',
    `lookback_days`              INT          NOT NULL DEFAULT 30 COMMENT '消耗率回溯天数',
    `near_expiry_days`           INT          NOT NULL DEFAULT 30 COMMENT '近效期预警阈值(天)',
    `safe_days`                  INT          NOT NULL DEFAULT 30 COMMENT 'SAFE阈值: 可用天数>=此值为SAFE',
    `low_days`                   INT          NOT NULL DEFAULT 20 COMMENT 'LOW阈值: 可用天数>=此值为LOW',
    `medium_days`                INT          NOT NULL DEFAULT 10 COMMENT 'MEDIUM阈值: 可用天数>=此值为MEDIUM',
    `high_days`                  INT          NOT NULL DEFAULT 5 COMMENT 'HIGH阈值: 可用天数>=此值为HIGH,<此值为CRITICAL',
    `safety_stock_days`          INT          NOT NULL DEFAULT 14 COMMENT '安全库存天数(触发采购建议)',
    `supplier_lead_time_default` INT          NOT NULL DEFAULT 7 COMMENT '默认供应商交货周期(天)',
    `suggestion_dedup_hours`     INT          NOT NULL DEFAULT 24 COMMENT '建议去重窗口(小时)',
    `is_active`                  INT          NOT NULL DEFAULT 0 COMMENT '0=非激活,1=激活(仅一条记录为1)',
    `created_by`                 VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_num` (`version_num`),
    KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货规则版本表';

-- 默认规则
INSERT INTO `biz_replenish_rule_version`
    (`version_num`, `lookback_days`, `near_expiry_days`, `safe_days`, `low_days`, `medium_days`, `high_days`, `safety_stock_days`, `supplier_lead_time_default`, `suggestion_dedup_hours`, `is_active`, `created_by`)
VALUES
    ('V1.0', 30, 30, 30, 20, 10, 5, 14, 7, 24, 1, 'SYSTEM');

-- 2. 库存风险快照表
CREATE TABLE IF NOT EXISTS `biz_inventory_risk_snapshot` (
    `id`                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `p_num`                    VARCHAR(64)   NOT NULL COMMENT '物资编号',
    `snapshot_time`            DATETIME      NOT NULL COMMENT '快照时间',
    `total_batch_quantity`     BIGINT        NOT NULL DEFAULT 0 COMMENT '批次总量',
    `locked_quantity`          BIGINT        NOT NULL DEFAULT 0 COMMENT '锁定量',
    `in_transit_quantity`      BIGINT        NOT NULL DEFAULT 0 COMMENT '在途量(APPROVED+SENT调拨)',
    `available_stock`          BIGINT        NOT NULL DEFAULT 0 COMMENT '可用库存=批次总量-锁定量-在途量',
    `daily_consumption_rate`   DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '日均消耗率',
    `lookback_days`            INT           NOT NULL COMMENT '消耗率回溯天数',
    `available_days`           DECIMAL(10,2) DEFAULT NULL COMMENT '可用天数=可用库存/日均消耗率',
    `near_expiry_batch_count`  INT           NOT NULL DEFAULT 0 COMMENT '近效期批次数',
    `near_expiry_quantity`     BIGINT        NOT NULL DEFAULT 0 COMMENT '近效期批次总量',
    `risk_level`               VARCHAR(16)   NOT NULL COMMENT 'SAFE/LOW/MEDIUM/HIGH/CRITICAL',
    `has_expiry_risk`          INT           NOT NULL DEFAULT 0 COMMENT '0=无过期风险,1=有过期风险',
    `rule_version_id`          BIGINT        NOT NULL COMMENT '使用的规则版本ID',
    `create_time`              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_p_num` (`p_num`),
    KEY `idx_snapshot_time` (`snapshot_time`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_p_num_snapshot` (`p_num`, `snapshot_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存风险快照表';

-- 3. 补货建议表
CREATE TABLE IF NOT EXISTS `biz_replenish_suggestion` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `suggestion_num`         VARCHAR(64)  NOT NULL COMMENT '建议单号',
    `p_num`                  VARCHAR(64)  NOT NULL COMMENT '物资编号',
    `suggestion_type`        VARCHAR(16)  NOT NULL COMMENT 'PURCHASE=采购建议,TRANSFER=调拨建议',
    `risk_snapshot_id`       BIGINT       DEFAULT NULL COMMENT '关联风险快照ID',
    `risk_level`             VARCHAR(16)  NOT NULL COMMENT '触发时的风险等级',
    `suggested_quantity`     BIGINT       NOT NULL COMMENT '建议数量',
    `from_department`        VARCHAR(128) DEFAULT NULL COMMENT '建议调出部门(仅TRANSFER)',
    `to_department`          VARCHAR(128) DEFAULT NULL COMMENT '建议调入部门(仅TRANSFER)',
    `supplier_id`            BIGINT       DEFAULT NULL COMMENT '建议供应商ID(仅PURCHASE)',
    `estimated_lead_days`    INT          DEFAULT NULL COMMENT '预计交货天数',
    `priority_batches`       VARCHAR(512) DEFAULT NULL COMMENT '优先消耗/调拨的批次号(逗号分隔)',
    `reason`                 VARCHAR(512) NOT NULL COMMENT '建议原因',
    `status`                 INT          NOT NULL DEFAULT 0 COMMENT '0=待处理,1=已采纳,2=已拒绝,3=已执行,4=已过期',
    `adopted_by`             VARCHAR(64)  DEFAULT NULL COMMENT '采纳人',
    `adopted_time`           DATETIME     DEFAULT NULL COMMENT '采纳时间',
    `related_transfer_num`   VARCHAR(64)  DEFAULT NULL COMMENT '关联的调拨单号(采纳后生成)',
    `related_in_num`         VARCHAR(64)  DEFAULT NULL COMMENT '关联的入库单号(采纳后生成)',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_suggestion_num` (`suggestion_num`),
    KEY `idx_p_num` (`p_num`),
    KEY `idx_status` (`status`),
    KEY `idx_suggestion_type` (`suggestion_type`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_p_num_type_status` (`p_num`, `suggestion_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货建议表';

-- 4. 供应商交付统计表
CREATE TABLE IF NOT EXISTS `biz_supplier_delivery_stat` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_id`            BIGINT       NOT NULL COMMENT '供应商ID',
    `p_num`                  VARCHAR(64)  DEFAULT NULL COMMENT '物资编号(NULL表示供应商整体)',
    `total_deliveries`       INT          NOT NULL DEFAULT 0 COMMENT '总交付次数',
    `on_time_deliveries`     INT          NOT NULL DEFAULT 0 COMMENT '准时交付次数',
    `avg_lead_days`          DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '平均交付天数',
    `max_lead_days`          INT          NOT NULL DEFAULT 0 COMMENT '最大交付天数',
    `min_lead_days`          INT          NOT NULL DEFAULT 0 COMMENT '最小交付天数',
    `on_time_rate`           DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '准时率(0~1.0000)',
    `last_delivery_time`     DATETIME     DEFAULT NULL COMMENT '最近交付时间',
    `promised_lead_days`     INT          DEFAULT NULL COMMENT '承诺交付天数',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `modified_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_pnum` (`supplier_id`, `p_num`),
    KEY `idx_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商交付统计表';

-- 5. 建议审计表
CREATE TABLE IF NOT EXISTS `biz_suggestion_audit` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `suggestion_id`          BIGINT       NOT NULL COMMENT '建议ID',
    `suggestion_num`         VARCHAR(64)  NOT NULL COMMENT '建议单号',
    `action`                 VARCHAR(32)  NOT NULL COMMENT 'CREATED/ADOPTED/REJECTED/EXECUTED/EXPIRED/TRANSFER_REJECTED',
    `before_status`          INT          DEFAULT NULL COMMENT '操作前状态',
    `after_status`           INT          NOT NULL COMMENT '操作后状态',
    `operator`               VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `remark`                 VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `related_num`            VARCHAR(64)  DEFAULT NULL COMMENT '关联业务单号',
    `event_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (`id`),
    KEY `idx_suggestion_id` (`suggestion_id`),
    KEY `idx_suggestion_num` (`suggestion_num`),
    KEY `idx_action` (`action`),
    KEY `idx_event_time` (`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建议审计表';

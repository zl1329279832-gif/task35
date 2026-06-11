-- 批次追溯事件表
-- 记录批次从入库、质检、锁定、出库、接收到异常回滚的全链路
CREATE TABLE IF NOT EXISTS `biz_batch_trace_event` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_number`           VARCHAR(64)  NOT NULL COMMENT '批次号',
    `p_num`                  VARCHAR(64)  DEFAULT NULL COMMENT '物资编号',
    `event_type`             VARCHAR(32)  NOT NULL COMMENT '事件类型: IN_STOCK/QUALITY_PASS/QUALITY_FAIL/LOCKED/UNLOCKED/DEDUCTED/TRANSFER_IN/ROLLBACK/EXPIRED',
    `related_num`            VARCHAR(64)  DEFAULT NULL COMMENT '关联业务单号(入库单/出库单/调拨单)',
    `quantity`               BIGINT       DEFAULT NULL COMMENT '本次操作数量',
    `before_quantity`        BIGINT       DEFAULT NULL COMMENT '操作前批次数量',
    `before_locked_quantity` BIGINT       DEFAULT NULL COMMENT '操作前锁定数量',
    `after_quantity`         BIGINT       DEFAULT NULL COMMENT '操作后批次数量',
    `after_locked_quantity`  BIGINT       DEFAULT NULL COMMENT '操作后锁定数量',
    `operator`               VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `remark`                 VARCHAR(512) DEFAULT NULL COMMENT '备注/原因',
    `event_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (`id`),
    KEY `idx_batch_number` (`batch_number`),
    KEY `idx_related_num` (`related_num`),
    KEY `idx_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次追溯事件表';

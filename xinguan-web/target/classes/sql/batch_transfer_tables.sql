-- 物资批次信息表
CREATE TABLE IF NOT EXISTS `biz_product_batch` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `p_num` varchar(64) NOT NULL COMMENT '物资编号',
  `batch_num` varchar(64) NOT NULL COMMENT '批次号',
  `supplier_id` bigint(20) DEFAULT NULL COMMENT '供应商ID',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期',
  `inspection_status` int(1) DEFAULT 0 COMMENT '质检状态: 0-待检, 1-合格, 2-不合格',
  `reserve_level` int(1) DEFAULT 1 COMMENT '储备等级: 1-普通, 2-重要, 3-紧急',
  `batch_stock` bigint(20) DEFAULT 0 COMMENT '批次库存数量',
  `locked_stock` bigint(20) DEFAULT 0 COMMENT '锁定库存数量',
  `in_num` varchar(64) DEFAULT NULL COMMENT '入库单号',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modified_time` datetime DEFAULT NULL COMMENT '修改时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_p_num` (`p_num`),
  KEY `idx_batch_num` (`batch_num`),
  KEY `idx_expiry_date` (`expiry_date`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物资批次信息表';

-- 跨部门调拨单表
CREATE TABLE IF NOT EXISTS `biz_stock_transfer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `transfer_num` varchar(64) NOT NULL COMMENT '调拨单号',
  `from_department_id` bigint(20) NOT NULL COMMENT '调出部门ID',
  `to_department_id` bigint(20) NOT NULL COMMENT '调入部门ID',
  `status` int(1) DEFAULT 0 COMMENT '调拨状态: 0-待审批, 1-已审批, 2-已拒绝, 3-已锁定库存, 4-已出库, 5-已接收, 6-已回滚',
  `approver` varchar(64) DEFAULT NULL COMMENT '审批人',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `operator` varchar(64) DEFAULT NULL COMMENT '发起人',
  `reason` varchar(500) NOT NULL COMMENT '调拨原因',
  `product_number` int(11) DEFAULT 0 COMMENT '物资总数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modified_time` datetime DEFAULT NULL COMMENT '修改时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_num` (`transfer_num`),
  KEY `idx_status` (`status`),
  KEY `idx_from_dept` (`from_department_id`),
  KEY `idx_to_dept` (`to_department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨部门调拨单表';

-- 调拨单明细表
CREATE TABLE IF NOT EXISTS `biz_stock_transfer_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `transfer_num` varchar(64) NOT NULL COMMENT '调拨单号',
  `p_num` varchar(64) NOT NULL COMMENT '物资编号',
  `batch_num` varchar(64) DEFAULT NULL COMMENT '批次号',
  `quantity` int(11) NOT NULL COMMENT '调拨数量',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `modified_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_transfer_num` (`transfer_num`),
  KEY `idx_p_num` (`p_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单明细表';

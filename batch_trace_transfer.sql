-- ============================================================
-- 防疫物资批次追溯与应急调拨 DDL
-- ============================================================

-- 1. 批次库存表
CREATE TABLE IF NOT EXISTS biz_product_batch (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  batch_number varchar(64) NOT NULL COMMENT '批次号',
  p_num varchar(64) NOT NULL COMMENT '物资编号',
  in_num varchar(64) NOT NULL COMMENT '入库单号',
  supplier_id bigint(20) DEFAULT NULL COMMENT '供应商ID',
  production_date date DEFAULT NULL COMMENT '生产日期',
  expiry_date date DEFAULT NULL COMMENT '有效期',
  quality_status int(11) NOT NULL DEFAULT 0 COMMENT '0=待检,1=合格,2=不合格,3=过期',
  reserve_level int(11) NOT NULL DEFAULT 1 COMMENT '1=常规,2=重点,3=战略',
  quantity bigint(20) NOT NULL DEFAULT 0 COMMENT '批次数量',
  locked_quantity bigint(20) NOT NULL DEFAULT 0 COMMENT '锁定数量',
  status int(11) NOT NULL DEFAULT 0 COMMENT '0=正常,1=回收',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  modified_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_number (batch_number),
  KEY idx_p_num (p_num),
  KEY idx_in_num (in_num),
  KEY idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 调拨申请表
CREATE TABLE IF NOT EXISTS biz_transfer_request (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  transfer_num varchar(64) NOT NULL COMMENT '调拨单号',
  p_num varchar(64) NOT NULL COMMENT '物资编号',
  transfer_quantity bigint(20) NOT NULL COMMENT '调拨数量',
  from_department varchar(128) NOT NULL COMMENT '调出部门',
  to_department varchar(128) NOT NULL COMMENT '调入部门',
  reason varchar(512) DEFAULT NULL,
  emergency_level int(11) NOT NULL DEFAULT 1 COMMENT '1=普通,2=紧急,3=特急',
  operator varchar(64) DEFAULT NULL,
  status int(11) NOT NULL DEFAULT 2 COMMENT '0=完成,1=拒绝,2=待审批,3=已审批,4=已发送,5=已接收,6=回滚',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  modified_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_num (transfer_num),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 调拨明细表
CREATE TABLE IF NOT EXISTS biz_transfer_request_info (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  transfer_num varchar(64) NOT NULL,
  batch_number varchar(64) NOT NULL,
  allocated_quantity bigint(20) NOT NULL,
  confirmed_quantity bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_transfer_num (transfer_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 修改现有表
ALTER TABLE biz_consumer
  ADD COLUMN is_isolation_point int(11) NOT NULL DEFAULT 0 COMMENT '0=否,1=是隔离点';

ALTER TABLE biz_in_stock_info
  ADD COLUMN batch_number varchar(64) DEFAULT NULL,
  ADD COLUMN production_date date DEFAULT NULL,
  ADD COLUMN expiry_date date DEFAULT NULL,
  ADD COLUMN quality_status int(11) NOT NULL DEFAULT 0,
  ADD COLUMN reserve_level int(11) NOT NULL DEFAULT 1;

ALTER TABLE biz_product_stock
  ADD COLUMN version int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

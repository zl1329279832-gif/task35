package com.coderman.business.service;

import com.coderman.common.vo.business.StockTransferVO;
import com.coderman.common.vo.system.PageVO;

/**
 * 跨部门调拨服务
 */
public interface StockTransferService {

    /**
     * 发起调拨申请
     */
    void createTransfer(StockTransferVO transferVO);

    /**
     * 审批通过
     */
    void approve(Long id, String approveRemark);

    /**
     * 审批拒绝(回滚)
     */
    void reject(Long id, String approveRemark);

    /**
     * 锁定库存
     */
    void lockStock(Long id);

    /**
     * 出库确认
     */
    void confirmShipment(Long id);

    /**
     * 接收确认
     */
    void confirmReceive(Long id);

    /**
     * 异常回滚
     */
    void rollback(Long id, String reason);

    /**
     * 调拨单列表
     */
    PageVO<StockTransferVO> findTransferList(Integer pageNum, Integer pageSize, StockTransferVO transferVO);

    /**
     * 调拨单详情
     */
    StockTransferVO detail(Long id);
}

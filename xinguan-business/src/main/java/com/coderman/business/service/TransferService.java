package com.coderman.business.service;

import com.coderman.common.vo.business.TransferRequestVO;
import com.coderman.common.vo.system.PageVO;

/**
 * 调拨服务接口
 */
public interface TransferService {

    /**
     * 创建调拨申请
     */
    TransferRequestVO createTransferRequest(TransferRequestVO vo);

    /**
     * 审批通过
     */
    void approve(Long id);

    /**
     * 审批拒绝
     */
    void reject(Long id);

    /**
     * 出库确认
     */
    void confirmSend(Long id);

    /**
     * 接收确认
     */
    void confirmReceive(Long id);

    /**
     * 异常回滚
     */
    void rollback(Long id, String reason);

    /**
     * 分页查询调拨列表
     */
    PageVO<TransferRequestVO> findTransferRequests(Integer pageNum, Integer pageSize, TransferRequestVO vo);

    /**
     * 查询调拨详情
     */
    TransferRequestVO getDetail(Long id);
}

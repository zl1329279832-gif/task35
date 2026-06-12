package com.coderman.business.mapper;

import com.coderman.common.model.business.TransferRequest;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 调拨申请Mapper
 */
public interface TransferRequestMapper extends Mapper<TransferRequest> {

    /**
     * 按物资编号汇总在途调拨量(APPROVED+SENT状态)
     */
    List<Map<String, Object>> sumInTransitByPNum();
}

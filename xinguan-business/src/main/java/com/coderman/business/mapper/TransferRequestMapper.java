package com.coderman.business.mapper;

import com.coderman.common.model.business.TransferRequest;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

/**
 * 调拨申请Mapper
 */
public interface TransferRequestMapper extends Mapper<TransferRequest> {

    /**
     * 悲观锁查询调拨单（防止并发状态变更）
     */
    TransferRequest findByIdForUpdate(@Param("id") Long id);
}

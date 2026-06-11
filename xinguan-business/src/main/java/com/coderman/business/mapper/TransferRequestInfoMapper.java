package com.coderman.business.mapper;

import com.coderman.common.model.business.TransferRequestInfo;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 调拨明细Mapper
 */
public interface TransferRequestInfoMapper extends Mapper<TransferRequestInfo> {

    List<TransferRequestInfo> findByTransferNum(@Param("transferNum") String transferNum);
}

package com.coderman.business.mapper;


import com.coderman.common.model.business.OutStockInfo;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author zhangyukang
 * @Date 2020/5/25 11:11
 * @Version 1.0
 **/
public interface OutStockInfoMapper extends Mapper<OutStockInfo> {

    /**
     * 按物资编号汇总指定时间之后的出库数量
     */
    List<Map<String, Object>> sumOutboundByPNumSince(@Param("sinceDate") Date sinceDate);
}

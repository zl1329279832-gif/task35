package com.coderman.business.mapper;

import com.coderman.common.model.business.StockWarningRule;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

/**
 * 库存预警规则Mapper
 */
public interface StockWarningRuleMapper extends Mapper<StockWarningRule> {

    /**
     * 查找物资级别的活跃规则
     */
    StockWarningRule findActiveRule(@Param("pNum") String pNum);

    /**
     * 查找全局默认规则
     */
    StockWarningRule findGlobalRule();
}

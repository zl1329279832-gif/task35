package com.coderman.business.mapper;

import com.coderman.common.model.business.ReplenishRuleVersion;
import tk.mybatis.mapper.common.Mapper;

/**
 * 补货规则版本Mapper
 */
public interface ReplenishRuleVersionMapper extends Mapper<ReplenishRuleVersion> {

    /**
     * 查询当前激活的规则版本
     */
    ReplenishRuleVersion findActiveRule();
}

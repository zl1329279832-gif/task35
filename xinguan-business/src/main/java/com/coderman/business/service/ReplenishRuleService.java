package com.coderman.business.service;

import com.coderman.common.vo.business.ReplenishRuleVersionVO;
import com.coderman.common.vo.system.PageVO;

/**
 * 补货规则版本服务
 */
public interface ReplenishRuleService {

    /**
     * 获取当前激活的规则
     */
    ReplenishRuleVersionVO getActiveRule();

    /**
     * 创建新规则版本
     */
    ReplenishRuleVersionVO createRule(ReplenishRuleVersionVO vo);

    /**
     * 激活指定规则版本(同时停用其他版本)
     */
    void activateRule(Long id);

    /**
     * 分页查询所有规则版本
     */
    PageVO<ReplenishRuleVersionVO> findRules(Integer pageNum, Integer pageSize);
}

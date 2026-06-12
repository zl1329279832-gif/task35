package com.coderman.business.service.imp;

import com.coderman.business.mapper.ReplenishRuleVersionMapper;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.vo.system.PageVO;
import com.coderman.common.vo.business.ReplenishRuleVersionVO;
import com.coderman.business.service.ReplenishRuleService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 补货规则版本服务实现
 */
@Service
public class ReplenishRuleServiceImpl implements ReplenishRuleService {

    @Autowired
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Override
    public ReplenishRuleVersionVO getActiveRule() {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }
        return convertToVO(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReplenishRuleVersionVO createRule(ReplenishRuleVersionVO vo) {
        ReplenishRuleVersion rule = new ReplenishRuleVersion();
        BeanUtils.copyProperties(vo, rule);
        rule.setIsActive(0);
        rule.setCreateTime(new Date());
        rule.setModifiedTime(new Date());
        ruleVersionMapper.insertSelective(rule);
        return convertToVO(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateRule(Long id) {
        ReplenishRuleVersion rule = ruleVersionMapper.selectByPrimaryKey(id);
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }

        // 停用所有版本
        Example deactivateExample = new Example(ReplenishRuleVersion.class);
        deactivateExample.createCriteria().andEqualTo("isActive", 1);
        ReplenishRuleVersion deactivate = new ReplenishRuleVersion();
        deactivate.setIsActive(0);
        deactivate.setModifiedTime(new Date());
        ruleVersionMapper.updateByExampleSelective(deactivate, deactivateExample);

        // 激活指定版本
        rule.setIsActive(1);
        rule.setModifiedTime(new Date());
        ruleVersionMapper.updateByPrimaryKeySelective(rule);
    }

    @Override
    public PageVO<ReplenishRuleVersionVO> findRules(Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(ReplenishRuleVersion.class);
        example.setOrderByClause("create_time desc");
        List<ReplenishRuleVersion> rules = ruleVersionMapper.selectByExample(example);
        PageInfo<ReplenishRuleVersion> pageInfo = new PageInfo<>(rules);

        List<ReplenishRuleVersionVO> voList = new ArrayList<>();
        for (ReplenishRuleVersion rule : rules) {
            voList.add(convertToVO(rule));
        }
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    private ReplenishRuleVersionVO convertToVO(ReplenishRuleVersion rule) {
        ReplenishRuleVersionVO vo = new ReplenishRuleVersionVO();
        BeanUtils.copyProperties(rule, vo);
        return vo;
    }
}

package com.coderman.controller.business;

import com.coderman.business.mapper.StockWarningRuleMapper;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.StockWarningRule;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.StockWarningRuleVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 预警规则管理
 */
@Api(tags = "预警规则管理")
@RestController
@RequestMapping("/warningRule")
public class StockWarningRuleController {

    @Autowired
    private StockWarningRuleMapper stockWarningRuleMapper;

    @ApiOperation("规则列表")
    @GetMapping("/list")
    @RequiresPermissions("warningRule:list")
    @ControllerEndpoint(exceptionMessage = "规则列表查询失败", operation = "预警规则列表查询")
    public ResponseBean findRules(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(StockWarningRule.class);
        example.createCriteria().andEqualTo("status", 0);
        example.setOrderByClause("create_time DESC");
        List<StockWarningRule> rules = stockWarningRuleMapper.selectByExample(example);

        List<StockWarningRuleVO> voList = new ArrayList<>();
        for (StockWarningRule rule : rules) {
            StockWarningRuleVO vo = new StockWarningRuleVO();
            BeanUtils.copyProperties(rule, vo);
            voList.add(vo);
        }
        PageInfo<StockWarningRule> pageInfo = new PageInfo<>(rules);
        return ResponseBean.success(new com.coderman.common.vo.system.PageVO<>(pageInfo.getTotal(), voList));
    }

    @ApiOperation("新增/更新规则")
    @PostMapping
    @RequiresPermissions("warningRule:add")
    @ControllerEndpoint(exceptionMessage = "规则保存失败", operation = "新增/更新预警规则")
    public ResponseBean saveRule(@RequestBody StockWarningRuleVO vo) {
        if (vo.getPNum() != null) {
            // 如果有物资级规则，将旧版本标记为历史
            StockWarningRule existing = stockWarningRuleMapper.findActiveRule(vo.getPNum());
            if (existing != null) {
                existing.setStatus(1); // 标记为历史
                stockWarningRuleMapper.updateByPrimaryKeySelective(existing);
            }
        } else {
            // 全局规则
            StockWarningRule existing = stockWarningRuleMapper.findGlobalRule();
            if (existing != null) {
                existing.setStatus(1);
                stockWarningRuleMapper.updateByPrimaryKeySelective(existing);
            }
        }

        // 创建新版本
        StockWarningRule rule = new StockWarningRule();
        BeanUtils.copyProperties(vo, rule);
        rule.setStatus(0); // 活跃
        rule.setVersion(vo.getVersion() != null ? vo.getVersion() + 1 : 1);
        rule.setCreateTime(new Date());
        rule.setModifiedTime(new Date());
        stockWarningRuleMapper.insertSelective(rule);

        StockWarningRuleVO result = new StockWarningRuleVO();
        BeanUtils.copyProperties(rule, result);
        return ResponseBean.success(result);
    }

    @ApiOperation("停用规则")
    @DeleteMapping("/{id}")
    @RequiresPermissions("warningRule:delete")
    @ControllerEndpoint(exceptionMessage = "规则停用失败", operation = "停用预警规则")
    public ResponseBean deleteRule(@PathVariable Long id) {
        StockWarningRule rule = stockWarningRuleMapper.selectByPrimaryKey(id);
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.WARNING_RULE_NOT_FOUND);
        }
        rule.setStatus(1); // 标记为历史
        rule.setModifiedTime(new Date());
        stockWarningRuleMapper.updateByPrimaryKeySelective(rule);
        return ResponseBean.success();
    }
}

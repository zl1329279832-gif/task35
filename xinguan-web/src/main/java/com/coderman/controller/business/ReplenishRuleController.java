package com.coderman.controller.business;

import com.coderman.business.service.ReplenishRuleService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.system.PageVO;
import com.coderman.common.vo.business.ReplenishRuleVersionVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 补货规则管理
 */
@Api(tags = "补货规则管理")
@RestController
@RequestMapping("/replenish-rule")
public class ReplenishRuleController {

    @Autowired
    private ReplenishRuleService ruleService;

    @ApiOperation(value = "获取当前激活规则")
    @RequiresPermissions("rule:list")
    @GetMapping("/active")
    public ResponseBean getActiveRule() {
        ReplenishRuleVersionVO vo = ruleService.getActiveRule();
        return ResponseBean.success(vo);
    }

    @ControllerEndpoint(exceptionMessage = "创建规则版本失败", operation = "创建补货规则版本")
    @ApiOperation(value = "创建新规则版本")
    @RequiresPermissions("rule:add")
    @PostMapping
    public ResponseBean createRule(@RequestBody ReplenishRuleVersionVO vo) {
        ReplenishRuleVersionVO result = ruleService.createRule(vo);
        return ResponseBean.success(result);
    }

    @ControllerEndpoint(exceptionMessage = "激活规则版本失败", operation = "激活补货规则版本")
    @ApiOperation(value = "激活规则版本")
    @RequiresPermissions("rule:edit")
    @PatchMapping("/activate/{id}")
    public ResponseBean activateRule(@PathVariable Long id) {
        ruleService.activateRule(id);
        return ResponseBean.success("规则已激活");
    }

    @ApiOperation(value = "分页查询规则版本列表")
    @RequiresPermissions("rule:list")
    @GetMapping("/list")
    public ResponseBean findRules(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageVO<ReplenishRuleVersionVO> pageVO = ruleService.findRules(pageNum, pageSize);
        return ResponseBean.success(pageVO);
    }
}

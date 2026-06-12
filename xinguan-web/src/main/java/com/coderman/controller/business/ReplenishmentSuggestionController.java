package com.coderman.controller.business;

import com.coderman.business.service.ReplenishmentSuggestionService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.ReplenishmentSuggestionVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 补货建议管理
 */
@Api(tags = "补货建议管理")
@RestController
@RequestMapping("/suggestion")
public class ReplenishmentSuggestionController {

    @Autowired
    private ReplenishmentSuggestionService replenishmentSuggestionService;

    @ApiOperation("建议列表")
    @GetMapping("/list")
    @RequiresPermissions("suggestion:list")
    @ControllerEndpoint(exceptionMessage = "建议列表查询失败", operation = "建议列表查询")
    public ResponseBean findSuggestions(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer suggestionType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageVO<ReplenishmentSuggestionVO> page = replenishmentSuggestionService
                .findSuggestions(status, suggestionType, pageNum, pageSize);
        return ResponseBean.success(page);
    }

    @ApiOperation("建议详情")
    @GetMapping("/{id}")
    @RequiresPermissions("suggestion:list")
    @ControllerEndpoint(exceptionMessage = "建议详情查询失败", operation = "建议详情查询")
    public ResponseBean getDetail(@PathVariable Long id) {
        ReplenishmentSuggestionVO detail = replenishmentSuggestionService.getSuggestionDetail(id);
        return ResponseBean.success(detail);
    }

    @ApiOperation("采纳建议")
    @PatchMapping("/adopt/{id}")
    @RequiresPermissions("suggestion:adopt")
    @ControllerEndpoint(exceptionMessage = "采纳建议失败", operation = "采纳补货建议")
    public ResponseBean adopt(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        replenishmentSuggestionService.adoptSuggestion(id, null, reason);
        return ResponseBean.success();
    }

    @ApiOperation("驳回建议")
    @PatchMapping("/reject/{id}")
    @RequiresPermissions("suggestion:reject")
    @ControllerEndpoint(exceptionMessage = "驳回建议失败", operation = "驳回补货建议")
    public ResponseBean reject(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        replenishmentSuggestionService.rejectSuggestion(id, null, reason);
        return ResponseBean.success();
    }

    @ApiOperation("手动触发建议生成")
    @PostMapping("/generate")
    @RequiresPermissions("suggestion:generate")
    @ControllerEndpoint(exceptionMessage = "建议生成失败", operation = "手动触发建议生成")
    public ResponseBean generate(@RequestParam Long snapshotId) {
        List<ReplenishmentSuggestionVO> suggestions = replenishmentSuggestionService
                .generateSuggestions(snapshotId);
        return ResponseBean.success(suggestions);
    }
}

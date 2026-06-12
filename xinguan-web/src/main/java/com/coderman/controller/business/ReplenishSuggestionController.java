package com.coderman.controller.business;

import com.coderman.business.service.ReplenishSuggestionService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.system.PageVO;
import com.coderman.common.vo.business.ReplenishSuggestionVO;
import com.coderman.common.vo.business.SuggestionAuditVO;
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
@RequestMapping("/replenish-suggestion")
public class ReplenishSuggestionController {

    @Autowired
    private ReplenishSuggestionService suggestionService;

    @ControllerEndpoint(exceptionMessage = "生成补货建议失败", operation = "生成所有物资补货建议")
    @ApiOperation(value = "生成所有物资补货建议")
    @RequiresPermissions("suggestion:generate")
    @PostMapping("/generate")
    public ResponseBean generateAll() {
        List<ReplenishSuggestionVO> suggestions = suggestionService.generateSuggestions();
        return ResponseBean.success(suggestions);
    }

    @ControllerEndpoint(exceptionMessage = "生成补货建议失败", operation = "生成单个物资补货建议")
    @ApiOperation(value = "生成单个物资补货建议")
    @RequiresPermissions("suggestion:generate")
    @PostMapping("/generate/{pNum}")
    public ResponseBean generateForProduct(@PathVariable String pNum) {
        List<ReplenishSuggestionVO> suggestions = suggestionService.generateSuggestionsForProduct(pNum);
        return ResponseBean.success(suggestions);
    }

    @ApiOperation(value = "分页查询建议列表")
    @RequiresPermissions("suggestion:list")
    @GetMapping("/list")
    public ResponseBean findSuggestions(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "pNum", required = false) String pNum,
            @RequestParam(value = "suggestionType", required = false) String suggestionType,
            @RequestParam(value = "status", required = false) Integer status) {
        PageVO<ReplenishSuggestionVO> pageVO = suggestionService.findSuggestions(
                pageNum, pageSize, pNum, suggestionType, status);
        return ResponseBean.success(pageVO);
    }

    @ApiOperation(value = "获取建议详情")
    @RequiresPermissions("suggestion:list")
    @GetMapping("/{id}")
    public ResponseBean getDetail(@PathVariable Long id) {
        ReplenishSuggestionVO vo = suggestionService.getDetail(id);
        if (vo == null) {
            return ResponseBean.error("补货建议不存在");
        }
        return ResponseBean.success(vo);
    }

    @ControllerEndpoint(exceptionMessage = "采纳建议失败", operation = "采纳补货建议")
    @ApiOperation(value = "采纳建议")
    @RequiresPermissions("suggestion:adopt")
    @PatchMapping("/adopt/{id}")
    public ResponseBean adopt(@PathVariable Long id) {
        suggestionService.adopt(id);
        return ResponseBean.success("建议已采纳");
    }

    @ControllerEndpoint(exceptionMessage = "拒绝建议失败", operation = "拒绝补货建议")
    @ApiOperation(value = "拒绝建议")
    @RequiresPermissions("suggestion:adopt")
    @PatchMapping("/reject/{id}")
    public ResponseBean reject(@PathVariable Long id,
                                @RequestParam(value = "reason", required = false) String reason) {
        suggestionService.reject(id, reason);
        return ResponseBean.success("建议已拒绝");
    }

    @ApiOperation(value = "建议追溯查询")
    @RequiresPermissions("suggestion:list")
    @GetMapping("/trace/{suggestionNum}")
    public ResponseBean trace(@PathVariable String suggestionNum) {
        List<SuggestionAuditVO> trail = suggestionService.getTrace(suggestionNum);
        return ResponseBean.success(trail);
    }

    @ControllerEndpoint(exceptionMessage = "调拨拒绝回调失败", operation = "调拨拒绝回调")
    @ApiOperation(value = "调拨拒绝回调(内部接口)")
    @PostMapping("/transfer-rejected/{transferNum}")
    public ResponseBean onTransferRejected(@PathVariable String transferNum) {
        suggestionService.onTransferRejected(transferNum);
        return ResponseBean.success();
    }
}

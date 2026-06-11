package com.coderman.controller.business;

import com.coderman.business.service.TransferService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.TransferRequestVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 物资调拨管理
 */
@Api(tags = "物资调拨管理")
@RestController
@RequestMapping("/transfer")
public class TransferController {

    @Autowired
    private TransferService transferService;

    @ApiOperation("创建调拨申请")
    @PostMapping
    @RequiresPermissions("transfer:add")
    @ControllerEndpoint(exceptionMessage = "创建调拨申请失败", operation = "创建调拨申请")
    public ResponseBean createTransferRequest(@RequestBody TransferRequestVO vo) {
        TransferRequestVO created = transferService.createTransferRequest(vo);
        return ResponseBean.success(created);
    }

    @ApiOperation("审批通过")
    @PatchMapping("/approve/{id}")
    @RequiresPermissions("transfer:approve")
    @ControllerEndpoint(exceptionMessage = "审批调拨失败", operation = "审批通过调拨")
    public ResponseBean approve(@PathVariable Long id) {
        transferService.approve(id);
        return ResponseBean.success();
    }

    @ApiOperation("审批拒绝")
    @PatchMapping("/reject/{id}")
    @RequiresPermissions("transfer:approve")
    @ControllerEndpoint(exceptionMessage = "拒绝调拨失败", operation = "审批拒绝调拨")
    public ResponseBean reject(@PathVariable Long id) {
        transferService.reject(id);
        return ResponseBean.success();
    }

    @ApiOperation("出库确认")
    @PatchMapping("/send/{id}")
    @RequiresPermissions("transfer:send")
    @ControllerEndpoint(exceptionMessage = "出库确认失败", operation = "调拨出库确认")
    public ResponseBean confirmSend(@PathVariable Long id) {
        transferService.confirmSend(id);
        return ResponseBean.success();
    }

    @ApiOperation("接收确认")
    @PatchMapping("/receive/{id}")
    @RequiresPermissions("transfer:receive")
    @ControllerEndpoint(exceptionMessage = "接收确认失败", operation = "调拨接收确认")
    public ResponseBean confirmReceive(@PathVariable Long id) {
        transferService.confirmReceive(id);
        return ResponseBean.success();
    }

    @ApiOperation("异常回滚")
    @PatchMapping("/rollback/{id}")
    @RequiresPermissions("transfer:rollback")
    @ControllerEndpoint(exceptionMessage = "调拨回滚失败", operation = "调拨异常回滚")
    public ResponseBean rollback(
            @PathVariable Long id,
            @RequestParam String reason) {
        transferService.rollback(id, reason);
        return ResponseBean.success();
    }

    @ApiOperation("调拨列表")
    @GetMapping("/list")
    @RequiresPermissions("transfer:list")
    @ControllerEndpoint(exceptionMessage = "调拨列表查询失败", operation = "调拨列表查询")
    public ResponseBean findTransferRequests(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            TransferRequestVO vo) {
        PageVO<TransferRequestVO> pageVO = transferService.findTransferRequests(pageNum, pageSize, vo);
        return ResponseBean.success(pageVO);
    }

    @ApiOperation("调拨详情")
    @GetMapping("/{id}")
    @RequiresPermissions("transfer:list")
    @ControllerEndpoint(exceptionMessage = "调拨详情查询失败", operation = "调拨详情查询")
    public ResponseBean getDetail(@PathVariable Long id) {
        TransferRequestVO vo = transferService.getDetail(id);
        if (vo == null) {
            return ResponseBean.error("调拨申请不存在");
        }
        return ResponseBean.success(vo);
    }
}

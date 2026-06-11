package com.coderman.controller.business;

import com.coderman.business.service.StockTransferService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.StockTransferVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 跨部门调拨控制器
 */
@Api(tags = "跨部门调拨接口")
@RestController
@RequestMapping("/stockTransfer")
public class StockTransferController {

    @Autowired
    private StockTransferService stockTransferService;

    /**
     * 调拨单列表
     */
    @ApiOperation(value = "调拨单列表")
    @GetMapping("/findTransferList")
    public ResponseBean findTransferList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            StockTransferVO transferVO) {
        PageVO<StockTransferVO> list = stockTransferService.findTransferList(pageNum, pageSize, transferVO);
        return ResponseBean.success(list);
    }

    /**
     * 发起调拨申请
     */
    @ControllerEndpoint(exceptionMessage = "调拨申请失败", operation = "发起调拨申请")
    @ApiOperation(value = "发起调拨申请")
    @PostMapping("/create")
    @RequiresPermissions({"transfer:create"})
    public ResponseBean create(@RequestBody @Validated StockTransferVO transferVO) {
        stockTransferService.createTransfer(transferVO);
        return ResponseBean.success();
    }

    /**
     * 调拨单详情
     */
    @ApiOperation(value = "调拨单详情")
    @GetMapping("/detail/{id}")
    public ResponseBean detail(@PathVariable Long id) {
        StockTransferVO detail = stockTransferService.detail(id);
        return ResponseBean.success(detail);
    }

    /**
     * 审批通过
     */
    @ControllerEndpoint(exceptionMessage = "调拨审批失败", operation = "调拨审批通过")
    @ApiOperation(value = "审批通过")
    @PutMapping("/approve/{id}")
    @RequiresPermissions({"transfer:approve"})
    public ResponseBean approve(@PathVariable Long id,
                                @RequestParam(value = "remark", required = false) String remark) {
        stockTransferService.approve(id, remark);
        return ResponseBean.success();
    }

    /**
     * 审批拒绝
     */
    @ControllerEndpoint(exceptionMessage = "调拨拒绝失败", operation = "调拨审批拒绝")
    @ApiOperation(value = "审批拒绝")
    @PutMapping("/reject/{id}")
    @RequiresPermissions({"transfer:approve"})
    public ResponseBean reject(@PathVariable Long id,
                               @RequestParam(value = "remark", required = false) String remark) {
        stockTransferService.reject(id, remark);
        return ResponseBean.success();
    }

    /**
     * 锁定库存
     */
    @ControllerEndpoint(exceptionMessage = "库存锁定失败", operation = "调拨锁定库存")
    @ApiOperation(value = "锁定库存")
    @PutMapping("/lockStock/{id}")
    @RequiresPermissions({"transfer:lock"})
    public ResponseBean lockStock(@PathVariable Long id) {
        stockTransferService.lockStock(id);
        return ResponseBean.success();
    }

    /**
     * 出库确认
     */
    @ControllerEndpoint(exceptionMessage = "出库确认失败", operation = "调拨出库确认")
    @ApiOperation(value = "出库确认")
    @PutMapping("/confirmShipment/{id}")
    @RequiresPermissions({"transfer:ship"})
    public ResponseBean confirmShipment(@PathVariable Long id) {
        stockTransferService.confirmShipment(id);
        return ResponseBean.success();
    }

    /**
     * 接收确认
     */
    @ControllerEndpoint(exceptionMessage = "接收确认失败", operation = "调拨接收确认")
    @ApiOperation(value = "接收确认")
    @PutMapping("/confirmReceive/{id}")
    @RequiresPermissions({"transfer:receive"})
    public ResponseBean confirmReceive(@PathVariable Long id) {
        stockTransferService.confirmReceive(id);
        return ResponseBean.success();
    }

    /**
     * 异常回滚
     */
    @ControllerEndpoint(exceptionMessage = "调拨回滚失败", operation = "调拨异常回滚")
    @ApiOperation(value = "异常回滚")
    @PutMapping("/rollback/{id}")
    @RequiresPermissions({"transfer:rollback"})
    public ResponseBean rollback(@PathVariable Long id,
                                 @RequestParam(value = "reason") String reason) {
        stockTransferService.rollback(id, reason);
        return ResponseBean.success();
    }
}

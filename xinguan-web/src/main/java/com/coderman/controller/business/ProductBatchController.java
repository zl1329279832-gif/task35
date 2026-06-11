package com.coderman.controller.business;

import com.coderman.business.service.ProductBatchService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.business.TraceVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 物资批次管理控制器
 */
@Api(tags = "物资批次管理接口")
@RestController
@RequestMapping("/productBatch")
public class ProductBatchController {

    @Autowired
    private ProductBatchService productBatchService;

    /**
     * 批次列表
     */
    @ApiOperation(value = "批次列表")
    @GetMapping("/findBatchList")
    public ResponseBean findBatchList(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "pNum", required = false) String pNum,
            @RequestParam(value = "batchNum", required = false) String batchNum) {
        PageVO<ProductBatchVO> list = productBatchService.findBatchList(pageNum, pageSize, pNum, batchNum);
        return ResponseBean.success(list);
    }

    /**
     * 近效期批次预警
     */
    @ApiOperation(value = "近效期批次预警")
    @GetMapping("/findNearExpiryBatches")
    public ResponseBean findNearExpiryBatches(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "days", defaultValue = "30") Integer days) {
        PageVO<ProductBatchVO> list = productBatchService.findNearExpiryBatches(pageNum, pageSize, days);
        return ResponseBean.success(list);
    }

    /**
     * 批次追溯查询
     */
    @ApiOperation(value = "批次追溯查询")
    @GetMapping("/trace/{batchNum}")
    public ResponseBean trace(@PathVariable String batchNum) {
        TraceVO trace = productBatchService.traceBatch(batchNum);
        return ResponseBean.success(trace);
    }

    /**
     * 更新质检状态
     */
    @ControllerEndpoint(exceptionMessage = "更新质检状态失败", operation = "更新批次质检状态")
    @ApiOperation(value = "更新质检状态")
    @PutMapping("/updateInspection/{id}")
    @RequiresPermissions({"productBatch:update"})
    public ResponseBean updateInspectionStatus(
            @PathVariable Long id,
            @RequestParam("status") Integer status) {
        productBatchService.updateInspectionStatus(id, status);
        return ResponseBean.success();
    }

    /**
     * 更新储备等级
     */
    @ControllerEndpoint(exceptionMessage = "更新储备等级失败", operation = "更新批次储备等级")
    @ApiOperation(value = "更新储备等级")
    @PutMapping("/updateReserveLevel/{id}")
    @RequiresPermissions({"productBatch:update"})
    public ResponseBean updateReserveLevel(
            @PathVariable Long id,
            @RequestParam("level") Integer level) {
        productBatchService.updateReserveLevel(id, level);
        return ResponseBean.success();
    }
}

package com.coderman.controller.business;

import com.coderman.business.service.ProductBatchService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物资批次管理
 */
@Api(tags = "物资批次管理")
@RestController
@RequestMapping("/batch")
public class ProductBatchController {

    @Autowired
    private ProductBatchService productBatchService;

    @ApiOperation("批次列表")
    @GetMapping("/list")
    @RequiresPermissions("batch:list")
    @ControllerEndpoint(exceptionMessage = "批次列表查询失败", operation = "批次列表查询")
    public ResponseBean findBatches(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            ProductBatchVO vo) {
        PageVO<ProductBatchVO> pageVO = productBatchService.findBatches(pageNum, pageSize, vo);
        return ResponseBean.success(pageVO);
    }

    @ApiOperation("批次追溯")
    @GetMapping("/trace/{batchNumber}")
    @RequiresPermissions("batch:trace")
    @ControllerEndpoint(exceptionMessage = "批次追溯查询失败", operation = "批次追溯查询")
    public ResponseBean getTraceability(@PathVariable String batchNumber) {
        ProductBatchVO vo = productBatchService.getTraceability(batchNumber);
        if (vo == null) {
            return ResponseBean.error("批次不存在: " + batchNumber);
        }
        return ResponseBean.success(vo);
    }

    @ApiOperation("更新质检状态")
    @PatchMapping("/quality/{id}")
    @RequiresPermissions("batch:quality")
    @ControllerEndpoint(exceptionMessage = "质检状态更新失败", operation = "更新质检状态")
    public ResponseBean updateQualityStatus(
            @PathVariable Long id,
            @RequestParam Integer qualityStatus) {
        productBatchService.updateQualityStatus(id, qualityStatus);
        return ResponseBean.success();
    }

    @ApiOperation("近效期批次")
    @GetMapping("/near-expiry")
    @RequiresPermissions("batch:list")
    @ControllerEndpoint(exceptionMessage = "近效期批次查询失败", operation = "近效期批次查询")
    public ResponseBean findNearExpiryBatches(
            @RequestParam(defaultValue = "30") int days) {
        List<ProductBatchVO> result = productBatchService.findNearExpiryBatches(days);
        return ResponseBean.success(result);
    }
}

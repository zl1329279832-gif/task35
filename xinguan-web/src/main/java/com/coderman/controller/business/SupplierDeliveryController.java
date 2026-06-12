package com.coderman.controller.business;

import com.coderman.business.service.SupplierDeliveryStatsService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.SupplierDeliveryStatsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商交付统计
 */
@Api(tags = "供应商交付统计")
@RestController
@RequestMapping("/supplierDelivery")
public class SupplierDeliveryController {

    @Autowired
    private SupplierDeliveryStatsService supplierDeliveryStatsService;

    @ApiOperation("按供应商查交付统计")
    @GetMapping("/bySupplier/{id}")
    @RequiresPermissions("supplierDelivery:list")
    @ControllerEndpoint(exceptionMessage = "供应商交付统计查询失败", operation = "按供应商查交付统计")
    public ResponseBean getBySupplier(@PathVariable Long id) {
        List<SupplierDeliveryStatsVO> stats = supplierDeliveryStatsService.getStatsBySupplier(id);
        return ResponseBean.success(stats);
    }

    @ApiOperation("按物资查交付统计")
    @GetMapping("/byProduct/{pNum}")
    @RequiresPermissions("supplierDelivery:list")
    @ControllerEndpoint(exceptionMessage = "物资交付统计查询失败", operation = "按物资查交付统计")
    public ResponseBean getByProduct(@PathVariable String pNum) {
        List<SupplierDeliveryStatsVO> stats = supplierDeliveryStatsService.getStatsByProduct(pNum);
        return ResponseBean.success(stats);
    }

    @ApiOperation("延迟供应商列表")
    @GetMapping("/delayed")
    @RequiresPermissions("supplierDelivery:list")
    @ControllerEndpoint(exceptionMessage = "延迟供应商查询失败", operation = "延迟供应商列表查询")
    public ResponseBean getDelayedSuppliers(
            @RequestParam(defaultValue = "0.8") double threshold) {
        List<SupplierDeliveryStatsVO> stats = supplierDeliveryStatsService.getDelayedSuppliers(threshold);
        return ResponseBean.success(stats);
    }
}

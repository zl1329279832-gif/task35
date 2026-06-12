package com.coderman.controller.business;

import com.coderman.business.service.SupplierDeliveryStatService;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.SupplierDeliveryStatVO;
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
@RequestMapping("/supplier-delivery")
public class SupplierDeliveryStatController {

    @Autowired
    private SupplierDeliveryStatService deliveryStatService;

    @ApiOperation(value = "查询供应商交付统计")
    @RequiresPermissions("supplier:list")
    @GetMapping("/supplier/{supplierId}")
    public ResponseBean findBySupplier(@PathVariable Long supplierId) {
        List<SupplierDeliveryStatVO> stats = deliveryStatService.findBySupplier(supplierId);
        return ResponseBean.success(stats);
    }

    @ApiOperation(value = "查询供应商特定物资交付统计")
    @RequiresPermissions("supplier:list")
    @GetMapping("/supplier/{supplierId}/{pNum}")
    public ResponseBean getStatForProduct(@PathVariable Long supplierId, @PathVariable String pNum) {
        SupplierDeliveryStatVO stat = deliveryStatService.getStatForProduct(supplierId, pNum);
        if (stat == null) {
            return ResponseBean.error("暂无该供应商的交付统计");
        }
        return ResponseBean.success(stat);
    }
}

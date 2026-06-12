package com.coderman.controller.business;

import com.coderman.business.service.InventoryRiskService;
import com.coderman.business.service.ReplenishSuggestionService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.InventoryRiskDashboardVO;
import com.coderman.common.vo.business.InventoryRiskSnapshotVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 库存风险管理
 */
@Api(tags = "库存风险管理")
@RestController
@RequestMapping("/inventory-risk")
public class InventoryRiskController {

    @Autowired
    private InventoryRiskService inventoryRiskService;

    @Autowired
    private ReplenishSuggestionService replenishSuggestionService;

    @ControllerEndpoint(exceptionMessage = "计算风险快照失败", operation = "计算所有物资风险快照")
    @ApiOperation(value = "计算所有物资风险快照")
    @RequiresPermissions("risk:calculate")
    @PostMapping("/calculate")
    public ResponseBean calculateAll() {
        inventoryRiskService.calculateAllRiskSnapshots();
        return ResponseBean.success("风险快照计算完成");
    }

    @ControllerEndpoint(exceptionMessage = "计算风险快照失败", operation = "计算单个物资风险快照")
    @ApiOperation(value = "计算单个物资风险快照")
    @RequiresPermissions("risk:calculate")
    @PostMapping("/calculate/{pNum}")
    public ResponseBean calculateForProduct(@PathVariable String pNum) {
        InventoryRiskSnapshotVO vo = inventoryRiskService.calculateRiskForProduct(pNum);
        return ResponseBean.success(vo);
    }

    @ApiOperation(value = "分页查询风险快照列表")
    @RequiresPermissions("risk:list")
    @GetMapping("/list")
    public ResponseBean findLatestRisks(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "riskLevel", required = false) String riskLevel) {
        PageVO<InventoryRiskSnapshotVO> pageVO = inventoryRiskService.findLatestRisks(pageNum, pageSize, riskLevel);
        return ResponseBean.success(pageVO);
    }

    @ApiOperation(value = "风险仪表盘")
    @RequiresPermissions("risk:list")
    @GetMapping("/dashboard")
    public ResponseBean dashboard() {
        InventoryRiskDashboardVO dashboard = inventoryRiskService.getRiskDashboard();
        return ResponseBean.success(dashboard);
    }

    @ApiOperation(value = "查询物资风险历史")
    @RequiresPermissions("risk:list")
    @GetMapping("/history/{pNum}")
    public ResponseBean riskHistory(
            @PathVariable String pNum,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        PageVO<InventoryRiskSnapshotVO> pageVO = inventoryRiskService.findRiskHistory(pNum, pageNum, pageSize);
        return ResponseBean.success(pageVO);
    }

    @ApiOperation(value = "获取物资最新风险")
    @RequiresPermissions("risk:list")
    @GetMapping("/{pNum}")
    public ResponseBean getLatestRisk(@PathVariable String pNum) {
        InventoryRiskSnapshotVO vo = inventoryRiskService.getLatestRisk(pNum);
        if (vo == null) {
            return ResponseBean.error("该物资暂无风险快照");
        }
        return ResponseBean.success(vo);
    }
}

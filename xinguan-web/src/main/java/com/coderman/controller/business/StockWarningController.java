package com.coderman.controller.business;

import com.coderman.business.service.StockWarningService;
import com.coderman.common.annotation.ControllerEndpoint;
import com.coderman.common.response.ResponseBean;
import com.coderman.common.vo.business.RiskDashboardVO;
import com.coderman.common.vo.business.StockRiskSnapshotVO;
import com.coderman.common.vo.system.PageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存预警管理
 */
@Api(tags = "库存预警管理")
@RestController
@RequestMapping("/warning")
public class StockWarningController {

    @Autowired
    private StockWarningService stockWarningService;

    @ApiOperation("风险概览")
    @GetMapping("/dashboard")
    @RequiresPermissions("warning:dashboard")
    @ControllerEndpoint(exceptionMessage = "风险概览查询失败", operation = "风险概览查询")
    public ResponseBean getDashboard() {
        RiskDashboardVO dashboard = stockWarningService.getRiskDashboard();
        return ResponseBean.success(dashboard);
    }

    @ApiOperation("快照列表")
    @GetMapping("/snapshots")
    @RequiresPermissions("warning:list")
    @ControllerEndpoint(exceptionMessage = "快照列表查询失败", operation = "快照列表查询")
    public ResponseBean findSnapshots(
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageVO<StockRiskSnapshotVO> page = stockWarningService.findSnapshots(riskLevel, pageNum, pageSize);
        return ResponseBean.success(page);
    }

    @ApiOperation("某物资的快照历史")
    @GetMapping("/snapshots/{pNum}")
    @RequiresPermissions("warning:list")
    @ControllerEndpoint(exceptionMessage = "快照历史查询失败", operation = "快照历史查询")
    public ResponseBean findSnapshotsByPNum(
            @PathVariable String pNum,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageVO<StockRiskSnapshotVO> page = stockWarningService.findSnapshotsByPNum(pNum, pageNum, pageSize);
        return ResponseBean.success(page);
    }

    @ApiOperation("手动触发全量风险重算")
    @PostMapping("/recalculate")
    @RequiresPermissions("warning:recalculate")
    @ControllerEndpoint(exceptionMessage = "全量风险重算失败", operation = "全量风险重算")
    public ResponseBean recalculateAll() {
        List<StockRiskSnapshotVO> snapshots = stockWarningService.generateAllRiskSnapshots();
        return ResponseBean.success(snapshots);
    }

    @ApiOperation("手动触发单个物资风险重算")
    @PostMapping("/recalculate/{pNum}")
    @RequiresPermissions("warning:recalculate")
    @ControllerEndpoint(exceptionMessage = "风险重算失败", operation = "单物资风险重算")
    public ResponseBean recalculate(@PathVariable String pNum) {
        StockRiskSnapshotVO snapshot = stockWarningService.generateRiskSnapshot(pNum);
        return ResponseBean.success(snapshot);
    }
}

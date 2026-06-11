package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.TransferServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ProductBatchTrace;
import com.coderman.common.model.business.ProductStock;
import com.coderman.common.model.business.TransferRequest;
import com.coderman.common.model.business.TransferRequestInfo;
import com.coderman.common.vo.business.BatchAllocationItemVO;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 调拨服务测试
 */
@RunWith(MockitoJUnitRunner.class)
public class TransferServiceTest {

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(ProductBatchTrace.class, config);
        EntityHelper.initEntityNameMap(ProductStock.class, config);
        EntityHelper.initEntityNameMap(TransferRequest.class, config);
        EntityHelper.initEntityNameMap(TransferRequestInfo.class, config);
    }

    @InjectMocks
    private TransferServiceImpl transferService;

    @Mock
    private TransferRequestMapper transferRequestMapper;

    @Mock
    private TransferRequestInfoMapper transferRequestInfoMapper;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @Mock
    private ProductStockMapper productStockMapper;

    @Mock
    private ProductBatchService productBatchService;

    @Captor
    private ArgumentCaptor<TransferRequest> transferRequestCaptor;

    @Captor
    private ArgumentCaptor<ProductBatch> productBatchCaptor;

    private TransferRequest pendingRequest;
    private TransferRequest approvedRequest;
    private TransferRequest sentRequest;
    private TransferRequest completedRequest;
    private TransferRequest rolledBackRequest;
    private ProductBatch productBatch;
    private ProductStock productStock;

    @Before
    public void setUp() {
        pendingRequest = new TransferRequest();
        pendingRequest.setId(1L);
        pendingRequest.setTransferNum("TRANSFER001");
        pendingRequest.setPNum("P001");
        pendingRequest.setTransferQuantity(30L);
        pendingRequest.setFromDepartment("仓库A");
        pendingRequest.setToDepartment("隔离点B");
        pendingRequest.setStatus(2); // 待审批
        pendingRequest.setEmergencyLevel(1);

        approvedRequest = new TransferRequest();
        approvedRequest.setId(2L);
        approvedRequest.setTransferNum("TRANSFER002");
        approvedRequest.setPNum("P001");
        approvedRequest.setTransferQuantity(30L);
        approvedRequest.setFromDepartment("仓库A");
        approvedRequest.setToDepartment("隔离点B");
        approvedRequest.setStatus(3); // 已审批

        sentRequest = new TransferRequest();
        sentRequest.setId(3L);
        sentRequest.setTransferNum("TRANSFER003");
        sentRequest.setPNum("P001");
        sentRequest.setTransferQuantity(30L);
        sentRequest.setFromDepartment("仓库A");
        sentRequest.setToDepartment("隔离点B");
        sentRequest.setStatus(4); // 已发送

        completedRequest = new TransferRequest();
        completedRequest.setId(4L);
        completedRequest.setTransferNum("TRANSFER004");
        completedRequest.setPNum("P001");
        completedRequest.setTransferQuantity(30L);
        completedRequest.setFromDepartment("仓库A");
        completedRequest.setToDepartment("隔离点B");
        completedRequest.setStatus(0); // 已完成

        rolledBackRequest = new TransferRequest();
        rolledBackRequest.setId(5L);
        rolledBackRequest.setTransferNum("TRANSFER005");
        rolledBackRequest.setPNum("P001");
        rolledBackRequest.setTransferQuantity(30L);
        rolledBackRequest.setFromDepartment("仓库A");
        rolledBackRequest.setToDepartment("隔离点B");
        rolledBackRequest.setStatus(6); // 已回滚

        productBatch = new ProductBatch();
        productBatch.setId(1L);
        productBatch.setBatchNumber("BATCH-001");
        productBatch.setPNum("P001");
        productBatch.setSupplierId(1L);
        productBatch.setQuantity(100L);
        productBatch.setLockedQuantity(0L);
        productBatch.setStatus(0);

        productStock = new ProductStock();
        productStock.setId(1L);
        productStock.setPNum("P001");
        productStock.setStock(200L);
        productStock.setVersion(0);
    }

    // ========== 创建调拨 ==========

    @Test
    public void testCreateTransferRequest_Success() {
        when(transferRequestMapper.insertSelective(any(TransferRequest.class))).thenReturn(1);

        com.coderman.common.vo.business.TransferRequestVO vo = new com.coderman.common.vo.business.TransferRequestVO();
        vo.setPNum("P001");
        vo.setTransferQuantity(30L);
        vo.setFromDepartment("仓库A");
        vo.setToDepartment("隔离点B");

        com.coderman.common.vo.business.TransferRequestVO result = transferService.createTransferRequest(vo);

        assertNotNull(result);
        assertNotNull(result.getTransferNum());
        assertEquals(Integer.valueOf(2), result.getStatus());
        verify(transferRequestMapper).insertSelective(any(TransferRequest.class));
    }

    // ========== 审批通过 ==========

    @Test
    public void testApprove_Success() {
        // 使用悲观锁查询
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(true);
        allocation.setAllocatedQuantity(30L);
        List<BatchAllocationItemVO> items = new ArrayList<>();
        BatchAllocationItemVO item = new BatchAllocationItemVO();
        item.setBatchNumber("BATCH-001");
        item.setAllocatedQuantity(30L);
        items.add(item);
        allocation.setItems(items);

        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.insertSelective(any(TransferRequestInfo.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.approve(1L);

        verify(productBatchService).lockBatches(anyList());
        verify(transferRequestInfoMapper).insertSelective(any(TransferRequestInfo.class));
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(3), transferRequestCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testApprove_WrongStatus_ThrowsException() {
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        transferService.approve(2L);
    }

    @Test(expected = ServiceException.class)
    public void testApprove_InsufficientStock_ThrowsException() {
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(false);
        allocation.setAllocatedQuantity(10L);
        allocation.setItems(new ArrayList<>());

        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);

        transferService.approve(1L);
    }

    // ========== 审批拒绝 ==========

    @Test
    public void testReject_Success() {
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.reject(1L);

        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(1), transferRequestCaptor.getValue().getStatus());
        // 审批拒绝不应触发任何批次操作（尚未分配和锁定）
        verify(productBatchService, never()).lockBatches(anyList());
        verify(productBatchService, never()).unlockBatches(anyList());
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
        verify(productBatchService, never()).restoreBatchQuantities(anyList());
    }

    @Test(expected = ServiceException.class)
    public void testReject_WrongStatus_ThrowsException() {
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        transferService.reject(2L); // 已审批状态不能再拒绝
    }

    // ========== 出库确认 ==========

    @Test
    public void testConfirmSend_Success() {
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(0L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(1);
        when(transferRequestInfoMapper.updateByPrimaryKeySelective(any(TransferRequestInfo.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.confirmSend(2L);

        // 验证库存扣减 (200 - 30 = 170)
        verify(productStockMapper).updateStockWithVersion("P001", 170L, 0);
        // 验证确认了批次扣减
        verify(productBatchService).confirmBatchDeductions(anyList());
        // 验证记录了OUT追溯事件
        verify(productBatchService).recordTraceEvent(
                eq("BATCH-001"), eq("P001"), eq("OUT"), eq(30L),
                eq("TRANSFER002"), anyString());
        // 验证状态更新为4
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(4), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testConfirmSend_DuplicateConfirmation_Idempotent() {
        // 已发送状态，重复确认应静默返回
        when(transferRequestMapper.findByIdForUpdate(3L)).thenReturn(sentRequest);

        transferService.confirmSend(3L);

        // 不应执行任何库存或批次操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
        verify(transferRequestMapper, never()).updateByPrimaryKeySelective(any(TransferRequest.class));
    }

    @Test(expected = ServiceException.class)
    public void testConfirmSend_OptimisticLockConflict_ThrowsException() {
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        // 乐观锁冲突：返回0
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(0);

        transferService.confirmSend(2L);
    }

    // ========== 接收确认 ==========

    @Test
    public void testConfirmReceive_Success() {
        when(transferRequestMapper.findByIdForUpdate(3L)).thenReturn(sentRequest);

        ProductBatch sourceBatch = new ProductBatch();
        sourceBatch.setId(1L);
        sourceBatch.setBatchNumber("BATCH-001");
        sourceBatch.setPNum("P001");
        sourceBatch.setSupplierId(1L);
        sourceBatch.setQualityStatus(1);
        sourceBatch.setReserveLevel(1);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER003");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(30L);

        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(230L), eq(0))).thenReturn(1);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER003"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.findTraceByBatchNumber("BATCH-001")).thenReturn(sourceBatch);
        when(productBatchService.createBatch(any(ProductBatch.class))).thenReturn(new ProductBatch());
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.confirmReceive(3L);

        // 验证恢复了目标库存 (200 + 30 = 230)
        verify(productStockMapper).updateStockWithVersion("P001", 230L, 0);
        // 验证创建了新批次
        verify(productBatchService).createBatch(any(ProductBatch.class));
        // 验证记录了RECEIVE追溯事件
        verify(productBatchService).recordTraceEvent(
                eq("BATCH-001"), eq("P001"), eq("RECEIVE"), eq(30L),
                eq("TRANSFER003"), anyString());
        // 验证状态更新为0（完成）
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(0), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testConfirmReceive_DuplicateConfirmation_Idempotent() {
        // 已完成状态，重复确认应静默返回
        when(transferRequestMapper.findByIdForUpdate(4L)).thenReturn(completedRequest);

        transferService.confirmReceive(4L);

        // 不应执行任何库存或批次操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchService, never()).createBatch(any(ProductBatch.class));
        verify(transferRequestMapper, never()).updateByPrimaryKeySelective(any(TransferRequest.class));
    }

    @Test(expected = ServiceException.class)
    public void testConfirmReceive_WrongStatus_ThrowsException() {
        // 待审批状态不能确认接收
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);

        transferService.confirmReceive(1L);
    }

    // ========== 回滚 ==========

    @Test
    public void testRollback_FromApproved_UnlocksBatches() {
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(0L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.rollback(2L, "测试回滚");

        // 验证解锁了批次（不是确认扣减）
        verify(productBatchService).unlockBatches(anyList());
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
        verify(productBatchService, never()).restoreBatchQuantities(anyList());
        // 验证状态更新为6
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testRollback_FromSent_UsesAtomicRestore() {
        when(transferRequestMapper.findByIdForUpdate(3L)).thenReturn(sentRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER003");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(30L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER003"))
                .thenReturn(Collections.singletonList(info));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(230L), eq(0))).thenReturn(1);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.rollback(3L, "发送后回滚");

        // 验证恢复了库存 (200 + 30 = 230)
        verify(productStockMapper).updateStockWithVersion("P001", 230L, 0);
        // 验证使用原子恢复（而非手动update）
        verify(productBatchService).restoreBatchQuantities(anyList());
        verify(productBatchMapper, never()).updateByPrimaryKeySelective(any(ProductBatch.class));
        // 验证记录了ROLLBACK追溯事件
        verify(productBatchService).recordTraceEvent(
                eq("BATCH-001"), eq("P001"), eq("ROLLBACK"), eq(30L),
                eq("TRANSFER003"), anyString());
        // 验证状态为6
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testRollback_AlreadyRolledBack_Idempotent() {
        // 已回滚状态，重复回滚应静默返回
        when(transferRequestMapper.findByIdForUpdate(5L)).thenReturn(rolledBackRequest);

        transferService.rollback(5L, "重复回滚");

        // 不应执行任何库存或批次操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchService, never()).unlockBatches(anyList());
        verify(productBatchService, never()).restoreBatchQuantities(anyList());
        verify(transferRequestMapper, never()).updateByPrimaryKeySelective(any(TransferRequest.class));
    }

    @Test(expected = ServiceException.class)
    public void testRollback_FromPending_ThrowsException() {
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);

        transferService.rollback(1L, "不能从待审批回滚");
    }

    // ========== 并发调拨 ==========

    @Test(expected = ServiceException.class)
    public void testConcurrentTransfer_StockConflict_ThrowsException() {
        // 模拟并发场景：两个调拨同时进行，乐观锁冲突
        when(transferRequestMapper.findByIdForUpdate(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(0L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.updateByPrimaryKeySelective(any(TransferRequestInfo.class))).thenReturn(1);

        // 悲观锁获取库存，但乐观锁更新失败（另一个事务先更新了version）
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(0);

        transferService.confirmSend(2L);
    }

    @Test(expected = ServiceException.class)
    public void testConcurrentTransfer_BatchLockConflict_ThrowsException() {
        // 模拟并发场景：两个调拨同时锁定同一批次，第二个失败
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(true);
        allocation.setAllocatedQuantity(30L);
        List<BatchAllocationItemVO> items = new ArrayList<>();
        BatchAllocationItemVO allocItem = new BatchAllocationItemVO();
        allocItem.setBatchNumber("BATCH-001");
        allocItem.setAllocatedQuantity(30L);
        items.add(allocItem);
        allocation.setItems(items);

        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.insertSelective(any(TransferRequestInfo.class))).thenReturn(1);
        // 批次锁定失败（并发冲突）
        doThrow(new ServiceException("批次锁定失败"))
                .when(productBatchService).lockBatches(anyList());

        transferService.approve(1L);
    }

    // ========== 库存锁定释放 ==========

    @Test
    public void testInventoryLockRelease_ApproveAndRollback_NoResidue() {
        // 审批 -> 回滚：验证锁定被完全释放，无残留

        // 步骤1：审批通过
        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(pendingRequest);
        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(true);
        allocation.setAllocatedQuantity(30L);
        List<BatchAllocationItemVO> items = new ArrayList<>();
        BatchAllocationItemVO allocItem = new BatchAllocationItemVO();
        allocItem.setBatchNumber("BATCH-001");
        allocItem.setAllocatedQuantity(30L);
        items.add(allocItem);
        allocation.setItems(items);

        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.insertSelective(any(TransferRequestInfo.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.approve(1L);

        // 验证锁定了30
        ArgumentCaptor<List<ProductBatchService.BatchLockItem>> lockCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(productBatchService).lockBatches(lockCaptor.capture());
        assertEquals(1, lockCaptor.getValue().size());
        assertEquals(Long.valueOf(30L), lockCaptor.getValue().get(0).getQuantity());

        // 步骤2：回滚释放
        reset(transferRequestMapper, transferRequestInfoMapper, productBatchMapper, productBatchService);

        TransferRequest nowApproved = new TransferRequest();
        nowApproved.setId(1L);
        nowApproved.setTransferNum("TRANSFER001");
        nowApproved.setPNum("P001");
        nowApproved.setTransferQuantity(30L);
        nowApproved.setFromDepartment("仓库A");
        nowApproved.setToDepartment("隔离点B");
        nowApproved.setStatus(3);

        when(transferRequestMapper.findByIdForUpdate(1L)).thenReturn(nowApproved);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setTransferNum("TRANSFER001");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(0L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER001"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.rollback(1L, "释放锁定");

        // 验证解锁了相同数量的30
        ArgumentCaptor<List<ProductBatchService.BatchLockItem>> unlockCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(productBatchService).unlockBatches(unlockCaptor.capture());
        assertEquals(1, unlockCaptor.getValue().size());
        assertEquals(Long.valueOf(30L), unlockCaptor.getValue().get(0).getQuantity());
    }
}

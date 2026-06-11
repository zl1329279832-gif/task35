package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.TransferServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.BatchTraceEvent;
import com.coderman.common.model.business.ProductBatch;
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
 * 覆盖：审批通过/拒绝、出库确认、接收确认(幂等)、并发调拨、回滚(已审批/已发送)、追溯事件
 */
@RunWith(MockitoJUnitRunner.class)
public class TransferServiceTest {

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(ProductStock.class, config);
        EntityHelper.initEntityNameMap(TransferRequest.class, config);
        EntityHelper.initEntityNameMap(TransferRequestInfo.class, config);
        EntityHelper.initEntityNameMap(BatchTraceEvent.class, config);
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

    @Mock
    private BatchTraceEventMapper batchTraceEventMapper;

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
        completedRequest.setStatus(0); // 已完成

        rolledBackRequest = new TransferRequest();
        rolledBackRequest.setId(5L);
        rolledBackRequest.setTransferNum("TRANSFER005");
        rolledBackRequest.setPNum("P001");
        rolledBackRequest.setTransferQuantity(30L);
        rolledBackRequest.setStatus(6); // 已回滚

        productBatch = new ProductBatch();
        productBatch.setId(1L);
        productBatch.setBatchNumber("BATCH-001");
        productBatch.setPNum("P001");
        productBatch.setQuantity(100L);
        productBatch.setLockedQuantity(30L);
        productBatch.setStatus(0);

        productStock = new ProductStock();
        productStock.setId(1L);
        productStock.setPNum("P001");
        productStock.setStock(200L);
        productStock.setVersion(0);
    }

    // ==================== 创建调拨申请 ====================

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
        assertEquals(Integer.valueOf(2), result.getStatus()); // 待审批
        verify(transferRequestMapper).insertSelective(any(TransferRequest.class));
    }

    // ==================== 审批通过 ====================

    @Test
    public void testApprove_Success() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = createAllocationResult("BATCH-001", 30L);
        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.insertSelective(any(TransferRequestInfo.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.approve(1L);

        // 验证锁定了批次（带关联单号）
        verify(productBatchService).lockBatches(anyList(), eq("TRANSFER001"));
        // 验证创建了调拨明细
        verify(transferRequestInfoMapper).insertSelective(any(TransferRequestInfo.class));
        // 验证状态更新为3(已审批)
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(3), transferRequestCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testApprove_WrongStatus_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);
        transferService.approve(2L);
    }

    @Test(expected = ServiceException.class)
    public void testApprove_InsufficientStock_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(false);
        allocation.setAllocatedQuantity(10L);
        allocation.setItems(new ArrayList<>());

        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);

        transferService.approve(1L);
    }

    // ==================== 并发调拨锁定冲突 ====================

    @Test(expected = ServiceException.class)
    public void testApprove_ConcurrentTransfer_LockConflict_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);

        BatchAllocationResultVO allocation = createAllocationResult("BATCH-001", 30L);
        when(productBatchService.allocateBatches(eq("P001"), eq(30L), (Long) isNull(), eq("NEAR_EXPIRY")))
                .thenReturn(allocation);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.insertSelective(any(TransferRequestInfo.class))).thenReturn(1);

        // 锁定失败（并发冲突）
        doThrow(new ServiceException(com.coderman.common.exception.ErrorCodeEnum.BATCH_LOCK_FAILED))
                .when(productBatchService).lockBatches(anyList(), eq("TRANSFER001"));

        transferService.approve(1L);
    }

    // ==================== 审批拒绝（无锁定残留） ====================

    @Test
    public void testReject_Success_NoLockResidue() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.reject(1L);

        // 验证状态更新为1(拒绝)
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(1), transferRequestCaptor.getValue().getStatus());

        // 审批拒绝时不应有任何批次锁定/解锁操作
        verify(productBatchService, never()).lockBatches(anyList());
        verify(productBatchService, never()).lockBatches(anyList(), anyString());
        verify(productBatchService, never()).unlockBatches(anyList());
        verify(productBatchService, never()).unlockBatches(anyList(), anyString());
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
    }

    @Test(expected = ServiceException.class)
    public void testReject_WrongStatus_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);
        transferService.reject(2L);
    }

    // ==================== 出库确认 ====================

    @Test
    public void testConfirmSend_Success() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER002", "BATCH-001", 30L, 0L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(1);
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestInfoMapper.updateByPrimaryKeySelective(any(TransferRequestInfo.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.confirmSend(2L);

        // 验证库存扣减 (200 - 30 = 170)
        verify(productStockMapper).updateStockWithVersion("P001", 170L, 0);
        // 验证确认了批次扣减（带关联单号）
        verify(productBatchService).confirmBatchDeductions(anyList(), eq("TRANSFER002"));
        // 验证状态更新为4(已发送)
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(4), transferRequestCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testConfirmSend_OptimisticLockConflict_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER002", "BATCH-001", 30L, 0L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(0);

        transferService.confirmSend(2L);
    }

    // ==================== 出库确认幂等（重复确认） ====================

    @Test
    public void testConfirmSend_DuplicateCall_Idempotent() {
        // 已发送状态再次调用confirmSend应幂等返回
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);

        transferService.confirmSend(3L);

        // 不应有任何库存操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
    }

    @Test(expected = ServiceException.class)
    public void testConfirmSend_WrongStatus_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);
        transferService.confirmSend(1L);
    }

    // ==================== 接收确认 ====================

    @Test
    public void testConfirmReceive_Success() {
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(230L), eq(0))).thenReturn(1);

        TransferRequestInfo info = createTransferInfo("TRANSFER003", "BATCH-001", 30L, 30L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER003"))
                .thenReturn(Collections.singletonList(info));

        ProductBatch sourceBatch = new ProductBatch();
        sourceBatch.setId(1L);
        sourceBatch.setBatchNumber("BATCH-001");
        sourceBatch.setPNum("P001");
        sourceBatch.setSupplierId(1L);
        sourceBatch.setQualityStatus(1);
        sourceBatch.setReserveLevel(1);
        when(productBatchMapper.findTraceByBatchNumber("BATCH-001")).thenReturn(sourceBatch);
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.confirmReceive(3L);

        // 验证库存增加 (200 + 30 = 230)
        verify(productStockMapper).updateStockWithVersion("P001", 230L, 0);
        // 验证创建了目标端新批次
        verify(productBatchMapper).insertSelective(any(ProductBatch.class));
        // 验证记录了TRANSFER_IN追溯事件
        verify(productBatchService).recordTraceEvent(
                anyString(), eq("P001"), eq("TRANSFER_IN"),
                eq("TRANSFER003"), eq(30L),
                eq(0L), eq(0L), eq(30L), eq(0L),
                isNull(), anyString());
        // 验证状态更新为0(完成)
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(0), transferRequestCaptor.getValue().getStatus());
    }

    // ==================== 接收确认幂等（重复接收） ====================

    @Test
    public void testConfirmReceive_DuplicateCall_Idempotent() {
        // 已完成状态再次调用confirmReceive应幂等返回
        when(transferRequestMapper.selectByPrimaryKey(4L)).thenReturn(completedRequest);

        transferService.confirmReceive(4L);

        // 不应有任何库存操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchMapper, never()).insertSelective(any(ProductBatch.class));
    }

    @Test(expected = ServiceException.class)
    public void testConfirmReceive_WrongStatus_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);
        transferService.confirmReceive(1L);
    }

    @Test(expected = ServiceException.class)
    public void testConfirmReceive_StockConflict_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(230L), eq(0))).thenReturn(0);

        transferService.confirmReceive(3L);
    }

    // ==================== 回滚：已审批状态 → 解锁批次 ====================

    @Test
    public void testRollback_FromApproved_UnlocksBatches() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER002", "BATCH-001", 30L, 0L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(productBatch));
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.rollback(2L, "测试回滚");

        // 验证解锁了批次（不是确认扣减）
        verify(productBatchService).unlockBatches(anyList(), eq("TRANSFER002"));
        verify(productBatchService, never()).confirmBatchDeductions(anyList());
        verify(productBatchService, never()).rollbackBatchDeductions(anyList(), anyString());
        // 验证状态更新为6(回滚)
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    // ==================== 回滚：已发送状态 → 恢复库存+恢复批次 ====================

    @Test
    public void testRollback_FromSent_ReversesOutStock() {
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER003", "BATCH-001", 30L, 30L);
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
        // 验证使用了原子回滚方法
        verify(productBatchService).rollbackBatchDeductions(anyList(), eq("TRANSFER003"));
        // 验证状态为6
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    // ==================== 回滚幂等（重复回滚） ====================

    @Test
    public void testRollback_DuplicateCall_Idempotent() {
        when(transferRequestMapper.selectByPrimaryKey(5L)).thenReturn(rolledBackRequest);

        transferService.rollback(5L, "重复回滚");

        // 不应有任何库存操作
        verify(productStockMapper, never()).findByPNumForUpdate(anyString());
        verify(productBatchService, never()).unlockBatches(anyList());
        verify(productBatchService, never()).rollbackBatchDeductions(anyList(), anyString());
    }

    // ==================== 回滚：非法状态 ====================

    @Test(expected = ServiceException.class)
    public void testRollback_FromPending_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);
        transferService.rollback(1L, "不应允许");
    }

    @Test(expected = ServiceException.class)
    public void testRollback_FromCompleted_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(4L)).thenReturn(completedRequest);
        transferService.rollback(4L, "不应允许");
    }

    // ==================== 回滚：乐观锁冲突 ====================

    @Test(expected = ServiceException.class)
    public void testRollback_FromSent_StockConflict_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER003", "BATCH-001", 30L, 30L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER003"))
                .thenReturn(Collections.singletonList(info));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(230L), eq(0))).thenReturn(0);

        transferService.rollback(3L, "乐观锁冲突");
    }

    // ==================== 查询 ====================

    @Test
    public void testGetDetail_Success() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = createTransferInfo("TRANSFER002", "BATCH-001", 30L, 0L);
        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productBatchMapper.findTraceByBatchNumber("BATCH-001")).thenReturn(productBatch);

        com.coderman.common.vo.business.TransferRequestVO detail = transferService.getDetail(2L);

        assertNotNull(detail);
        assertEquals("TRANSFER002", detail.getTransferNum());
        assertNotNull(detail.getDetails());
        assertEquals(1, detail.getDetails().size());
    }

    @Test
    public void testGetDetail_NotFound() {
        when(transferRequestMapper.selectByPrimaryKey(999L)).thenReturn(null);
        assertNull(transferService.getDetail(999L));
    }

    // ==================== 辅助方法 ====================

    private BatchAllocationResultVO createAllocationResult(String batchNumber, Long qty) {
        BatchAllocationResultVO allocation = new BatchAllocationResultVO();
        allocation.setFullyAllocated(true);
        allocation.setAllocatedQuantity(qty);
        List<BatchAllocationItemVO> items = new ArrayList<>();
        BatchAllocationItemVO item = new BatchAllocationItemVO();
        item.setBatchNumber(batchNumber);
        item.setAllocatedQuantity(qty);
        items.add(item);
        allocation.setItems(items);
        return allocation;
    }

    private TransferRequestInfo createTransferInfo(String transferNum, String batchNumber,
                                                    Long allocated, Long confirmed) {
        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum(transferNum);
        info.setBatchNumber(batchNumber);
        info.setAllocatedQuantity(allocated);
        info.setConfirmedQuantity(confirmed);
        return info;
    }
}

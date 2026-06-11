package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.TransferServiceImpl;
import com.coderman.common.exception.ServiceException;
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

        productBatch = new ProductBatch();
        productBatch.setId(1L);
        productBatch.setBatchNumber("BATCH-001");
        productBatch.setPNum("P001");
        productBatch.setQuantity(100L);
        productBatch.setLockedQuantity(0L);
        productBatch.setStatus(0);

        productStock = new ProductStock();
        productStock.setId(1L);
        productStock.setPNum("P001");
        productStock.setStock(200L);
        productStock.setVersion(0);
    }

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

    @Test
    public void testApprove_Success() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);

        // mock 批次分配结果
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

        // 验证锁定了批次
        verify(productBatchService).lockBatches(anyList());
        // 验证创建了调拨明细
        verify(transferRequestInfoMapper).insertSelective(any(TransferRequestInfo.class));
        // 验证状态更新为3
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

    @Test
    public void testReject_Success() {
        when(transferRequestMapper.selectByPrimaryKey(1L)).thenReturn(pendingRequest);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.reject(1L);

        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(1), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testRollback_FromApproved_UnlocksBatches() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

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
        // 验证状态更新为6
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testRollback_FromSent_ReversesOutStock() {
        when(transferRequestMapper.selectByPrimaryKey(3L)).thenReturn(sentRequest);

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
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);
        when(transferRequestMapper.updateByPrimaryKeySelective(any(TransferRequest.class))).thenReturn(1);

        transferService.rollback(3L, "发送后回滚");

        // 验证恢复了库存 (200 + 30 = 230)
        verify(productStockMapper).updateStockWithVersion("P001", 230L, 0);
        // 验证恢复了批次数量
        verify(productBatchMapper).updateByPrimaryKeySelective(productBatchCaptor.capture());
        assertEquals(Long.valueOf(130L), productBatchCaptor.getValue().getQuantity()); // 100 + 30
        // 验证状态为6
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(6), transferRequestCaptor.getValue().getStatus());
    }

    @Test
    public void testConfirmSend_Success() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);
        info.setConfirmedQuantity(0L);

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
        // 验证确认了批次扣减
        verify(productBatchService).confirmBatchDeductions(anyList());
        // 验证状态更新为4
        verify(transferRequestMapper).updateByPrimaryKeySelective(transferRequestCaptor.capture());
        assertEquals(Integer.valueOf(4), transferRequestCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testConfirmSend_OptimisticLockConflict_ThrowsException() {
        when(transferRequestMapper.selectByPrimaryKey(2L)).thenReturn(approvedRequest);

        TransferRequestInfo info = new TransferRequestInfo();
        info.setId(1L);
        info.setTransferNum("TRANSFER002");
        info.setBatchNumber("BATCH-001");
        info.setAllocatedQuantity(30L);

        when(transferRequestInfoMapper.findByTransferNum("TRANSFER002"))
                .thenReturn(Collections.singletonList(info));
        when(productStockMapper.findByPNumForUpdate("P001")).thenReturn(productStock);
        // 乐观锁冲突：返回0
        when(productStockMapper.updateStockWithVersion(eq("P001"), eq(170L), eq(0))).thenReturn(0);

        transferService.confirmSend(2L);
    }
}

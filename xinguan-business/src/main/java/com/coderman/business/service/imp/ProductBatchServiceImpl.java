package com.coderman.business.service.imp;

import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.service.ProductBatchService;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.business.TraceVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 物资批次服务实现
 */
@Service
@Transactional
public class ProductBatchServiceImpl implements ProductBatchService {

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Override
    public void addBatch(ProductBatch batch) {
        batch.setCreateTime(new Date());
        batch.setModifiedTime(new Date());
        if (batch.getLockedStock() == null) {
            batch.setLockedStock(0L);
        }
        if (batch.getInspectionStatus() == null) {
            batch.setInspectionStatus(0);
        }
        if (batch.getReserveLevel() == null) {
            batch.setReserveLevel(1);
        }
        productBatchMapper.insert(batch);
    }

    @Override
    public void updateInspectionStatus(Long batchId, Integer status) {
        ProductBatch batch = productBatchMapper.selectByPrimaryKey(batchId);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        batch.setInspectionStatus(status);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);
    }

    @Override
    public void updateReserveLevel(Long batchId, Integer level) {
        ProductBatch batch = productBatchMapper.selectByPrimaryKey(batchId);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        batch.setReserveLevel(level);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);
    }

    /**
     * 按先进先出+近效期优先分配批次
     * 分配策略:
     * 1. 只选质检合格(inspection_status=1)的批次
     * 2. 只选未过期的批次
     * 3. 按有效期升序(近效期优先), 再按创建时间升序(先进先出)
     * 4. 如果是隔离点需求, 优先分配储备等级为3(紧急)的批次
     */
    @Override
    public List<ProductBatch> allocateBatches(String pNum, int quantity, boolean isolationPriority) {
        List<ProductBatch> availableBatches = productBatchMapper.findAvailableBatchesFIFO(pNum);
        if (CollectionUtils.isEmpty(availableBatches)) {
            throw new ServiceException(ErrorCodeEnum.PRODUCT_STOCK_ERROR);
        }

        // 如果是隔离点需求优先, 将紧急储备等级的批次排到前面
        if (isolationPriority) {
            List<ProductBatch> urgentBatches = new ArrayList<>();
            List<ProductBatch> normalBatches = new ArrayList<>();
            for (ProductBatch batch : availableBatches) {
                if (batch.getReserveLevel() != null && batch.getReserveLevel() == 3) {
                    urgentBatches.add(batch);
                } else {
                    normalBatches.add(batch);
                }
            }
            availableBatches = new ArrayList<>();
            availableBatches.addAll(urgentBatches);
            availableBatches.addAll(normalBatches);
        }

        List<ProductBatch> allocated = new ArrayList<>();
        int remaining = quantity;

        for (ProductBatch batch : availableBatches) {
            if (remaining <= 0) break;

            long available = batch.getBatchStock() - batch.getLockedStock();
            if (available <= 0) continue;

            int toAllocate = (int) Math.min(remaining, available);
            // 创建一个分配记录, 用batchStock字段临时存放本次分配数量
            ProductBatch allocationRecord = new ProductBatch();
            allocationRecord.setId(batch.getId());
            allocationRecord.setBatchNum(batch.getBatchNum());
            allocationRecord.setPNum(batch.getPNum());
            allocationRecord.setBatchStock((long) toAllocate);
            allocationRecord.setExpiryDate(batch.getExpiryDate());
            allocationRecord.setReserveLevel(batch.getReserveLevel());
            allocated.add(allocationRecord);

            remaining -= toAllocate;
        }

        if (remaining > 0) {
            throw new ServiceException(ErrorCodeEnum.BATCH_STOCK_NOT_ENOUGH);
        }

        return allocated;
    }

    @Override
    public void lockBatchStock(String batchNum, long quantity) {
        Example example = new Example(ProductBatch.class);
        example.createCriteria().andEqualTo("batchNum", batchNum);
        ProductBatch batch = productBatchMapper.selectOneByExample(example);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        long available = batch.getBatchStock() - batch.getLockedStock();
        if (available < quantity) {
            throw new ServiceException(ErrorCodeEnum.BATCH_STOCK_NOT_ENOUGH);
        }
        batch.setLockedStock(batch.getLockedStock() + quantity);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);
    }

    @Override
    public void unlockBatchStock(String batchNum, long quantity) {
        Example example = new Example(ProductBatch.class);
        example.createCriteria().andEqualTo("batchNum", batchNum);
        ProductBatch batch = productBatchMapper.selectOneByExample(example);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        long newLocked = batch.getLockedStock() - quantity;
        if (newLocked < 0) {
            newLocked = 0;
        }
        batch.setLockedStock(newLocked);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);
    }

    @Override
    public void deductBatchStock(String batchNum, long quantity) {
        Example example = new Example(ProductBatch.class);
        example.createCriteria().andEqualTo("batchNum", batchNum);
        ProductBatch batch = productBatchMapper.selectOneByExample(example);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        if (batch.getBatchStock() < quantity) {
            throw new ServiceException(ErrorCodeEnum.BATCH_STOCK_NOT_ENOUGH);
        }
        batch.setBatchStock(batch.getBatchStock() - quantity);
        // 同时释放对应的锁定量
        long lockedToRelease = Math.min(batch.getLockedStock(), quantity);
        batch.setLockedStock(batch.getLockedStock() - lockedToRelease);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);
    }

    @Override
    public PageVO<ProductBatchVO> findNearExpiryBatches(Integer pageNum, Integer pageSize, int days) {
        PageHelper.startPage(pageNum, pageSize);
        List<ProductBatchVO> list = productBatchMapper.findNearExpiryBatches(days);
        PageInfo<ProductBatchVO> pageInfo = new PageInfo<>(list);
        return new PageVO<>(pageInfo.getTotal(), list);
    }

    @Override
    public PageVO<ProductBatchVO> findBatchList(Integer pageNum, Integer pageSize, String pNum, String batchNum) {
        PageHelper.startPage(pageNum, pageSize);
        List<ProductBatchVO> list = productBatchMapper.findBatchList(pNum, batchNum);
        PageInfo<ProductBatchVO> pageInfo = new PageInfo<>(list);
        return new PageVO<>(pageInfo.getTotal(), list);
    }

    @Override
    public TraceVO traceBatch(String batchNum) {
        TraceVO trace = productBatchMapper.traceBatch(batchNum);
        if (trace == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        return trace;
    }
}

package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.ProductBatchService;
import com.coderman.business.service.StockTransferService;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.response.ActiveUser;
import com.coderman.common.vo.business.StockTransferVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.*;

/**
 * 跨部门调拨服务实现
 *
 * 调拨流程: 发起申请(0) -> 审批通过(1)/拒绝(2) -> 锁定库存(3) -> 出库确认(4) -> 接收确认(5)
 * 任何环节异常可回滚(6)
 */
@Service
@Transactional
public class StockTransferServiceImpl implements StockTransferService {

    @Autowired
    private StockTransferMapper stockTransferMapper;

    @Autowired
    private StockTransferItemMapper stockTransferItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductStockMapper productStockMapper;

    @Autowired
    private ProductBatchService productBatchService;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    /**
     * 发起调拨申请
     */
    @Override
    public void createTransfer(StockTransferVO transferVO) {
        List<Object> items = transferVO.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_ITEMS_EMPTY);
        }

        String transferNum = UUID.randomUUID().toString().substring(0, 32).replace("-", "");
        int totalNumber = 0;

        for (Object item : items) {
            LinkedHashMap map = (LinkedHashMap) item;
            String pNum = (String) map.get("pNum");
            int quantity = (int) map.get("quantity");
            String batchNum = map.get("batchNum") != null ? (String) map.get("batchNum") : null;

            // 校验物资存在
            Example productExample = new Example(Product.class);
            productExample.createCriteria().andEqualTo("pNum", pNum);
            List<Product> products = productMapper.selectByExample(productExample);
            if (CollectionUtils.isEmpty(products)) {
                throw new ServiceException(ErrorCodeEnum.PRODUCT_NOT_FOUND);
            }

            // 校验库存
            Example stockExample = new Example(ProductStock.class);
            stockExample.createCriteria().andEqualTo("pNum", pNum);
            ProductStock stock = productStockMapper.selectOneByExample(stockExample);
            if (stock == null || stock.getStock() < quantity) {
                throw new ServiceException(ErrorCodeEnum.PRODUCT_STOCK_ERROR);
            }

            totalNumber += quantity;

            // 插入调拨明细
            StockTransferItem transferItem = new StockTransferItem();
            transferItem.setTransferNum(transferNum);
            transferItem.setPNum(pNum);
            transferItem.setBatchNum(batchNum);
            transferItem.setQuantity(quantity);
            transferItem.setCreateTime(new Date());
            transferItem.setModifiedTime(new Date());
            stockTransferItemMapper.insert(transferItem);
        }

        // 创建调拨单
        StockTransfer transfer = new StockTransfer();
        BeanUtils.copyProperties(transferVO, transfer);
        transfer.setTransferNum(transferNum);
        transfer.setProductNumber(totalNumber);
        transfer.setStatus(0); // 待审批
        transfer.setCreateTime(new Date());
        transfer.setModifiedTime(new Date());

        ActiveUser activeUser = (ActiveUser) SecurityUtils.getSubject().getPrincipal();
        transfer.setOperator(activeUser.getUser().getUsername());

        stockTransferMapper.insert(transfer);
    }

    /**
     * 审批通过
     */
    @Override
    public void approve(Long id, String approveRemark) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (transfer.getStatus() != 0) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        ActiveUser activeUser = (ActiveUser) SecurityUtils.getSubject().getPrincipal();
        transfer.setStatus(1); // 已审批
        transfer.setApprover(activeUser.getUser().getUsername());
        transfer.setApproveTime(new Date());
        transfer.setApproveRemark(approveRemark);
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 审批拒绝
     */
    @Override
    public void reject(Long id, String approveRemark) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (transfer.getStatus() != 0) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        ActiveUser activeUser = (ActiveUser) SecurityUtils.getSubject().getPrincipal();
        transfer.setStatus(2); // 已拒绝
        transfer.setApprover(activeUser.getUser().getUsername());
        transfer.setApproveTime(new Date());
        transfer.setApproveRemark(approveRemark);
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 锁定库存 - 审批通过后锁定对应批次库存
     */
    @Override
    public void lockStock(Long id) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (transfer.getStatus() != 1) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 查询调拨明细
        Example itemExample = new Example(StockTransferItem.class);
        itemExample.createCriteria().andEqualTo("transferNum", transfer.getTransferNum());
        List<StockTransferItem> items = stockTransferItemMapper.selectByExample(itemExample);

        if (CollectionUtils.isEmpty(items)) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_ITEMS_EMPTY);
        }

        // 逐项锁定批次库存
        for (StockTransferItem item : items) {
            if (item.getBatchNum() != null && !item.getBatchNum().isEmpty()) {
                // 指定了批次号, 直接锁定该批次
                productBatchService.lockBatchStock(item.getBatchNum(), item.getQuantity());
            } else {
                // 未指定批次, 按FIFO+近效期自动分配并锁定
                List<ProductBatch> allocated = productBatchService.allocateBatches(
                        item.getPNum(), item.getQuantity(), false);
                // 用第一个分配的批次号更新明细(简化处理)
                if (!CollectionUtils.isEmpty(allocated)) {
                    item.setBatchNum(allocated.get(0).getBatchNum());
                    item.setModifiedTime(new Date());
                    stockTransferItemMapper.updateByPrimaryKeySelective(item);
                    // 锁定所有分配的批次
                    for (ProductBatch batch : allocated) {
                        productBatchService.lockBatchStock(batch.getBatchNum(), batch.getBatchStock());
                    }
                }
            }
        }

        transfer.setStatus(3); // 已锁定库存
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 出库确认 - 扣减库存
     */
    @Override
    public void confirmShipment(Long id) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (transfer.getStatus() != 3) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        Example itemExample = new Example(StockTransferItem.class);
        itemExample.createCriteria().andEqualTo("transferNum", transfer.getTransferNum());
        List<StockTransferItem> items = stockTransferItemMapper.selectByExample(itemExample);

        for (StockTransferItem item : items) {
            // 扣减总库存
            Example stockExample = new Example(ProductStock.class);
            stockExample.createCriteria().andEqualTo("pNum", item.getPNum());
            ProductStock productStock = productStockMapper.selectOneByExample(stockExample);
            if (productStock == null || productStock.getStock() < item.getQuantity()) {
                throw new ServiceException(ErrorCodeEnum.PRODUCT_STOCK_ERROR);
            }
            productStock.setStock(productStock.getStock() - item.getQuantity());
            productStockMapper.updateByPrimaryKey(productStock);

            // 扣减批次库存
            if (item.getBatchNum() != null && !item.getBatchNum().isEmpty()) {
                productBatchService.deductBatchStock(item.getBatchNum(), item.getQuantity());
            }
        }

        transfer.setStatus(4); // 已出库
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 接收确认
     */
    @Override
    public void confirmReceive(Long id) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (transfer.getStatus() != 4) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        transfer.setStatus(5); // 已接收
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 异常回滚 - 释放锁定库存, 恢复状态
     */
    @Override
    public void rollback(Long id, String reason) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        // 只有已审批(1)、已锁定库存(3)、已出库(4)状态可以回滚
        int status = transfer.getStatus();
        if (status != 1 && status != 3 && status != 4) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 如果已锁定库存, 需要释放锁定
        if (status == 3) {
            Example itemExample = new Example(StockTransferItem.class);
            itemExample.createCriteria().andEqualTo("transferNum", transfer.getTransferNum());
            List<StockTransferItem> items = stockTransferItemMapper.selectByExample(itemExample);

            for (StockTransferItem item : items) {
                if (item.getBatchNum() != null && !item.getBatchNum().isEmpty()) {
                    productBatchService.unlockBatchStock(item.getBatchNum(), item.getQuantity());
                }
            }
        }

        // 如果已出库, 需要回补库存
        if (status == 4) {
            Example itemExample = new Example(StockTransferItem.class);
            itemExample.createCriteria().andEqualTo("transferNum", transfer.getTransferNum());
            List<StockTransferItem> items = stockTransferItemMapper.selectByExample(itemExample);

            for (StockTransferItem item : items) {
                // 回补总库存
                Example stockExample = new Example(ProductStock.class);
                stockExample.createCriteria().andEqualTo("pNum", item.getPNum());
                ProductStock productStock = productStockMapper.selectOneByExample(stockExample);
                if (productStock != null) {
                    productStock.setStock(productStock.getStock() + item.getQuantity());
                    productStockMapper.updateByPrimaryKey(productStock);
                }
                // 回补批次库存
                if (item.getBatchNum() != null && !item.getBatchNum().isEmpty()) {
                    Example batchExample = new Example(ProductBatch.class);
                    batchExample.createCriteria().andEqualTo("batchNum", item.getBatchNum());
                    ProductBatch batch = productBatchMapper.selectOneByExample(batchExample);
                    if (batch != null) {
                        batch.setBatchStock(batch.getBatchStock() + item.getQuantity());
                        batch.setModifiedTime(new Date());
                        productBatchMapper.updateByPrimaryKeySelective(batch);
                    }
                }
            }
        }

        transfer.setStatus(6); // 已回滚
        transfer.setRemark(reason);
        transfer.setModifiedTime(new Date());
        stockTransferMapper.updateByPrimaryKeySelective(transfer);
    }

    /**
     * 调拨单列表
     */
    @Override
    public PageVO<StockTransferVO> findTransferList(Integer pageNum, Integer pageSize, StockTransferVO transferVO) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(StockTransfer.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("create_time desc");

        if (transferVO.getTransferNum() != null && !"".equals(transferVO.getTransferNum())) {
            criteria.andLike("transferNum", "%" + transferVO.getTransferNum() + "%");
        }
        if (transferVO.getStatus() != null) {
            criteria.andEqualTo("status", transferVO.getStatus());
        }

        List<StockTransfer> transfers = stockTransferMapper.selectByExample(example);
        List<StockTransferVO> voList = new ArrayList<>();
        for (StockTransfer transfer : transfers) {
            StockTransferVO vo = new StockTransferVO();
            BeanUtils.copyProperties(transfer, vo);
            voList.add(vo);
        }
        PageInfo<StockTransfer> pageInfo = new PageInfo<>(transfers);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    /**
     * 调拨单详情
     */
    @Override
    public StockTransferVO detail(Long id) {
        StockTransfer transfer = stockTransferMapper.selectByPrimaryKey(id);
        if (transfer == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        StockTransferVO vo = new StockTransferVO();
        BeanUtils.copyProperties(transfer, vo);

        // 查询明细
        Example itemExample = new Example(StockTransferItem.class);
        itemExample.createCriteria().andEqualTo("transferNum", transfer.getTransferNum());
        List<StockTransferItem> items = stockTransferItemMapper.selectByExample(itemExample);
        List<Object> itemList = new ArrayList<>();
        for (StockTransferItem item : items) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("id", item.getId());
            itemMap.put("pNum", item.getPNum());
            itemMap.put("batchNum", item.getBatchNum());
            itemMap.put("quantity", item.getQuantity());

            // 查询物资名称
            Example productExample = new Example(Product.class);
            productExample.createCriteria().andEqualTo("pNum", item.getPNum());
            List<Product> products = productMapper.selectByExample(productExample);
            if (!CollectionUtils.isEmpty(products)) {
                Product product = products.get(0);
                itemMap.put("productName", product.getName());
                itemMap.put("model", product.getModel());
                itemMap.put("unit", product.getUnit());
            }
            itemList.add(itemMap);
        }
        vo.setItems(itemList);
        return vo;
    }
}

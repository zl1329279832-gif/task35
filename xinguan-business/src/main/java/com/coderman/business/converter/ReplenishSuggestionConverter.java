package com.coderman.business.converter;

import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ReplenishSuggestion;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.vo.business.ReplenishSuggestionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.List;

/**
 * 补货建议转换器
 */
@Component
public class ReplenishSuggestionConverter {

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private ProductMapper productMapper;

    public List<ReplenishSuggestionVO> converterToVOList(List<ReplenishSuggestion> suggestions) {
        List<ReplenishSuggestionVO> voList = new ArrayList<>();
        for (ReplenishSuggestion suggestion : suggestions) {
            voList.add(converterToVO(suggestion));
        }
        return voList;
    }

    public ReplenishSuggestionVO converterToVO(ReplenishSuggestion suggestion) {
        ReplenishSuggestionVO vo = new ReplenishSuggestionVO();
        BeanUtils.copyProperties(suggestion, vo);

        // 产品名称
        Example pe = new Example(Product.class);
        pe.createCriteria().andEqualTo("pNum", suggestion.getPNum());
        Product product = productMapper.selectOneByExample(pe);
        if (product != null) {
            vo.setProductName(product.getName());
        }

        // 供应商名称
        if (suggestion.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectByPrimaryKey(suggestion.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
        }

        return vo;
    }
}

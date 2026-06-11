package com.tk.ai.video.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tk.ai.video.module.product.entity.ProductImageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ProductImageMapper extends BaseMapper<ProductImageEntity> {

    @Select("SELECT * FROM product_images WHERE product_id = #{productId} ORDER BY is_primary DESC")
    List<ProductImageEntity> findByProductId(UUID productId);
}

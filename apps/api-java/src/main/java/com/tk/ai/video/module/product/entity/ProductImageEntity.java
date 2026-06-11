package com.tk.ai.video.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("product_images")
public class ProductImageEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID productId;
    private UUID userId;
    private String url;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private Boolean isPrimary;
    private OffsetDateTime createdAt;
}

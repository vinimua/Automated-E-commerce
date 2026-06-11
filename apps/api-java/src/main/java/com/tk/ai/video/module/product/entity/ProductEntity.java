package com.tk.ai.video.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@TableName("products")
public class ProductEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private UUID userId;
    private String name;
    private String description;
    private String productLink;
    private String category;
    private String targetMarket;
    private String language;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> sellingPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> painPoints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetAudience;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scenes;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> recommendedVideoTypes;

    private Integer videoScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> riskTips;

    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

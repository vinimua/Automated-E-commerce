package com.tk.ai.video.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tk.ai.video.common.json.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@TableName(value = "products", autoResultMap = true)
public class ProductEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;
    private String name;
    private String description;
    private String productLink;
    private String category;
    private String targetMarket;
    private String language;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> sellingPoints;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> painPoints;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> targetAudience;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> scenes;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> recommendedVideoTypes;

    private Integer videoScore;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> riskTips;

    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

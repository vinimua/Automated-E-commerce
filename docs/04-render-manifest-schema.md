# 文档 4：RenderManifest Schema 文档

## 1. 文档说明

RenderManifest 是 Python AI 编排服务与 Node Render Worker 之间的视频渲染协议。

它是渲染服务的唯一输入合同。Render Worker 不理解商品、用户、额度、任务状态等业务概念，只理解 RenderManifest。

---

## 2. RenderManifest 作用

RenderManifest 定义以下内容：

1. 使用哪个视频模板。
2. 视频分辨率、帧率、时长。
3. 每个镜头使用的素材。
4. 每个镜头的字幕。
5. 每个镜头的动效和转场。
6. 字幕样式。
7. 背景音乐。
8. 封面生成规则。
9. 可选元数据。

---

## 3. RenderManifest JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/render-manifest.schema.json",
  "title": "RenderManifest",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "manifestVersion",
    "taskId",
    "videoId",
    "videoType",
    "template",
    "resolution",
    "fps",
    "duration",
    "assets",
    "subtitleStyle",
    "music",
    "cover",
    "output"
  ],
  "properties": {
    "manifestVersion": {
      "type": "string",
      "const": "1.0.0"
    },
    "taskId": {
      "type": "string",
      "format": "uuid"
    },
    "videoId": {
      "type": "string",
      "format": "uuid"
    },
    "videoType": {
      "type": "string",
      "enum": [
        "pain_point_solution",
        "before_after",
        "review",
        "product_showcase",
        "ugc_style",
        "tutorial"
      ]
    },
    "template": {
      "type": "string",
      "enum": [
        "pain_point_solution_v1",
        "before_after_v1",
        "review_v1"
      ]
    },
    "resolution": {
      "type": "string",
      "enum": [
        "1080x1920"
      ]
    },
    "fps": {
      "type": "integer",
      "enum": [
        30
      ]
    },
    "duration": {
      "type": "integer",
      "minimum": 15,
      "maximum": 30
    },
    "assets": {
      "type": "array",
      "minItems": 4,
      "maxItems": 12,
      "items": {
        "$ref": "#/$defs/RenderAsset"
      }
    },
    "subtitleStyle": {
      "$ref": "#/$defs/SubtitleStyle"
    },
    "music": {
      "$ref": "#/$defs/MusicConfig"
    },
    "cover": {
      "$ref": "#/$defs/CoverConfig"
    },
    "voiceover": {
      "$ref": "#/$defs/VoiceoverConfig"
    },
    "output": {
      "$ref": "#/$defs/OutputConfig"
    },
    "metadata": {
      "type": "object",
      "additionalProperties": true
    }
  },
  "$defs": {
    "RenderAsset": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "shotNo",
        "type",
        "duration",
        "subtitle",
        "edit"
      ],
      "properties": {
        "shotNo": {
          "type": "integer",
          "minimum": 1
        },
        "type": {
          "type": "string",
          "enum": [
            "image",
            "video",
            "product_image",
            "text"
          ]
        },
        "url": {
          "type": "string",
          "format": "uri"
        },
        "textContent": {
          "type": "string",
          "minLength": 1,
          "maxLength": 120
        },
        "duration": {
          "type": "integer",
          "minimum": 1,
          "maximum": 8
        },
        "subtitle": {
          "type": "string",
          "minLength": 1,
          "maxLength": 90
        },
        "edit": {
          "$ref": "#/$defs/EditConfig"
        }
      },
      "allOf": [
        {
          "if": {
            "properties": {
              "type": {
                "const": "text"
              }
            },
            "required": ["type"]
          },
          "then": {
            "required": ["textContent"]
          },
          "else": {
            "required": ["url"]
          }
        }
      ]
    },
    "EditConfig": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "transition",
        "zoom",
        "position"
      ],
      "properties": {
        "transition": {
          "type": "string",
          "enum": [
            "none",
            "quick_cut",
            "fade",
            "zoom_in",
            "slide_up",
            "slide_left",
            "flash"
          ]
        },
        "zoom": {
          "type": "string",
          "enum": [
            "none",
            "slow_in",
            "slow_out",
            "pulse",
            "fast_in"
          ]
        },
        "position": {
          "type": "string",
          "enum": [
            "center",
            "top",
            "bottom",
            "left",
            "right"
          ]
        },
        "crop": {
          "type": "string",
          "enum": [
            "cover",
            "contain"
          ],
          "default": "cover"
        }
      }
    },
    "SubtitleStyle": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "fontSize",
        "position",
        "maxLines",
        "background"
      ],
      "properties": {
        "fontSize": {
          "type": "integer",
          "minimum": 36,
          "maximum": 72
        },
        "position": {
          "type": "string",
          "enum": [
            "bottom_center",
            "middle_center",
            "top_center"
          ]
        },
        "maxLines": {
          "type": "integer",
          "minimum": 1,
          "maximum": 2
        },
        "background": {
          "type": "string",
          "enum": [
            "none",
            "semi_transparent",
            "solid"
          ]
        },
        "safeAreaBottom": {
          "type": "integer",
          "minimum": 0,
          "maximum": 300,
          "default": 180
        }
      }
    },
    "MusicConfig": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "type",
        "volume"
      ],
      "properties": {
        "type": {
          "type": "string",
          "enum": [
            "default",
            "uploaded",
            "none"
          ]
        },
        "url": {
          "type": [
            "string",
            "null"
          ],
          "format": "uri"
        },
        "volume": {
          "type": "number",
          "minimum": 0,
          "maximum": 1
        }
      }
    },
    "VoiceoverConfig": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "enabled",
        "url",
        "volume"
      ],
      "properties": {
        "enabled": {
          "type": "boolean"
        },
        "url": {
          "type": [
            "string",
            "null"
          ],
          "format": "uri"
        },
        "volume": {
          "type": "number",
          "minimum": 0,
          "maximum": 1
        }
      }
    },
    "OutputConfig": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "format",
        "codec",
        "bitrate"
      ],
      "properties": {
        "format": {
          "type": "string",
          "enum": ["mp4"]
        },
        "codec": {
          "type": "string",
          "enum": ["h264"]
        },
        "bitrate": {
          "type": "string",
          "pattern": "^[1-9][0-9]*M$"
        }
      }
    },
    "CoverConfig": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "text",
        "sourceShotNo"
      ],
      "properties": {
        "text": {
          "type": "string",
          "minLength": 1,
          "maxLength": 80
        },
        "sourceShotNo": {
          "type": "integer",
          "minimum": 1
        }
      }
    }
  }
}
```

---

## 4. RenderManifest 示例

```json
{
  "manifestVersion": "1.0.0",
  "taskId": "0f1386d6-c9de-487a-a861-d6fd1dd33d70",
  "videoId": "f2ae28c4-b60f-4e78-9f82-e3a15b44dd59",
  "videoType": "pain_point_solution",
  "template": "pain_point_solution_v1",
  "resolution": "1080x1920",
  "fps": 30,
  "duration": 22,
  "assets": [
    {
      "shotNo": 1,
      "type": "video",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-clips/shot-1.mp4",
      "duration": 3,
      "subtitle": "Still cleaning this by hand?",
      "edit": {
        "transition": "quick_cut",
        "zoom": "none",
        "position": "center",
        "crop": "cover"
      }
    },
    {
      "shotNo": 2,
      "type": "product_image",
      "url": "https://cos.example.com/users/u1/products/p1/images/product.png",
      "duration": 4,
      "subtitle": "This tool makes it easier",
      "edit": {
        "transition": "zoom_in",
        "zoom": "slow_in",
        "position": "center",
        "crop": "contain"
      }
    },
    {
      "shotNo": 3,
      "type": "image",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-images/shot-3.png",
      "duration": 5,
      "subtitle": "Watch the dirt disappear",
      "edit": {
        "transition": "quick_cut",
        "zoom": "slow_in",
        "position": "center",
        "crop": "cover"
      }
    },
    {
      "shotNo": 4,
      "type": "image",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-images/shot-4.png",
      "duration": 5,
      "subtitle": "Perfect for bathroom and kitchen",
      "edit": {
        "transition": "fade",
        "zoom": "slow_out",
        "position": "center",
        "crop": "cover"
      }
    },
    {
      "shotNo": 5,
      "type": "product_image",
      "url": "https://cos.example.com/users/u1/products/p1/images/product.png",
      "duration": 5,
      "subtitle": "Clean smarter, not harder",
      "edit": {
        "transition": "quick_cut",
        "zoom": "pulse",
        "position": "center",
        "crop": "contain"
      }
    }
  ],
  "subtitleStyle": {
    "fontSize": 54,
    "position": "bottom_center",
    "maxLines": 2,
    "background": "semi_transparent",
    "safeAreaBottom": 180
  },
  "music": {
    "type": "default",
    "url": null,
    "volume": 0.35
  },
  "voiceover": {
    "enabled": false,
    "url": null,
    "volume": 0.8
  },
  "cover": {
    "text": "Clean smarter, not harder",
    "sourceShotNo": 2
  },
  "output": {
    "format": "mp4",
    "codec": "h264",
    "bitrate": "8M"
  },
  "metadata": {
    "productName": "Electric Cleaning Brush",
    "targetMarket": "US",
    "language": "en"
  }
}
```

---

## 5. Render Worker 校验流程

```text
1. 收到 RabbitMQ 消息。
2. 解析 renderManifest。
3. 使用 JSON Schema 校验。
4. 校验 template 是否存在。
5. 校验 assets 数量。
6. 校验非 text 素材 URL 是否可访问。
7. 校验 text 素材必须存在 textContent。
8. 校验总 duration 与 assets duration 总和一致，允许误差不超过 1 秒。
9. 校验字幕长度。
10. 校验音乐、配音和输出配置。
11. 校验通过后开始渲染。
```

---

## 6. Render Worker 失败策略

| 失败点 | 处理方式 |
|---|---|
| JSON Schema 校验失败 | 直接回调 Java failed |
| 素材 URL 不可访问 | 重试下载 3 次 |
| 单个 AI 视频无法读取 | 尝试降级为对应封面帧或图片 |
| Remotion 渲染失败 | RabbitMQ 重试 |
| FFmpeg 转码失败 | RabbitMQ 重试 |
| COS 上传失败 | 重试 3 次 |
| Java 回调失败 | 重试 3 次，仍失败记录本地日志 |

---

## 7. 模板和素材类型映射

| material type | Render 行为 |
|---|---|
| image | 图片全屏铺满，按 edit.crop 处理 |
| product_image | 商品图居中展示，可加缩放动效 |
| video | 视频片段裁剪为 9:16 |
| text | 纯文字动画镜头，使用 textContent，不需要 url |

---

## 8. V1 模板要求

### 8.1 pain_point_solution_v1

结构：

```text
0-3 秒：痛点画面
3-7 秒：商品出现
7-14 秒：展示解决过程
14-19 秒：展示效果
19-22 秒：商品特写 + 引导
```

### 8.2 before_after_v1

结构：

```text
0-2 秒：展示结果
2-6 秒：展示使用前
6-14 秒：展示使用过程
14-20 秒：前后对比
20-25 秒：商品特写
```

### 8.3 review_v1

结构：

```text
0-3 秒：提出问题
3-8 秒：展示商品
8-18 秒：测试过程
18-23 秒：测试结果
23-30 秒：推荐理由
```

---

## 9. V2+ 模板扩展

| 模板 | 说明 |
|---|---|
| product_showcase_v2 | 商品展示型 |
| ugc_style_v2 | UGC 种草型 |
| comment_reply_v3 | 评论区回复型 |
| listicle_v3 | 清单推荐型 |
| account_style_custom_v4 | 根据账号风格定制 |

---

## 10. 版本管理建议

RenderManifest V1 必须增加版本控制。

后续建议扩展：

```json
{
  "manifestVersion": "1.0.0"
}
```

V1 强制 `manifestVersion = "1.0.0"`，避免模板升级后旧任务无法渲染。

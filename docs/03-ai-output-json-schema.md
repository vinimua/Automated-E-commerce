# 文档 3：AI JSON Schema 文档

## 1. 文档说明

本文档用于约束 Python AI 编排服务的所有结构化输出，确保 Java 后端、Python AI 服务、Node Render Worker 可以稳定对接。

适用范围：

- 商品分析输出
- 视频方案输出
- 脚本分镜输出
- 素材生成输出
- 质量检查输出
- AI 回调 Payload

---

## 2. AI 输出总规则

AI 服务所有输出必须满足以下要求：

1. 必须是合法 JSON。
2. 禁止 Markdown。
3. 禁止自然语言解释。
4. 禁止代码块包裹。
5. 禁止额外字段，除非 Schema 允许 `additionalProperties`。
6. 必填字段必须存在。
7. 数组字段即使为空也必须返回 `[]`。
8. 分数类字段范围为 `0-100`。
9. 文案必须基于真实商品信息，不得编造虚假功效。
10. 输出必须能被 Python Pydantic / JSON Schema 校验通过。
11. 回调 Java 的 Payload 必须携带 `schemaVersion`，V1 固定为 `1.0.0`。
12. 阶段结果使用 `stage + status` 表达，禁止用业务任务状态代替阶段执行状态。

--- 

## 3. ProductAnalysis Schema

### 3.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/product-analysis.schema.json",
  "title": "ProductAnalysis",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "category",
    "sellingPoints",
    "painPoints",
    "targetAudience",
    "scenes",
    "recommendedVideoTypes",
    "videoScore",
    "riskTips"
  ],
  "properties": {
    "category": {
      "type": "string",
      "minLength": 1
    },
    "sellingPoints": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "string",
        "minLength": 1
      }
    },
    "painPoints": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "string",
        "minLength": 1
      }
    },
    "targetAudience": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "string",
        "minLength": 1
      }
    },
    "scenes": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "string",
        "minLength": 1
      }
    },
    "recommendedVideoTypes": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "string",
        "enum": [
          "pain_point_solution",
          "before_after",
          "review",
          "product_showcase",
          "ugc_style",
          "tutorial"
        ]
      }
    },
    "videoScore": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "riskTips": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "claimRiskLevel": {
      "type": "string",
      "enum": ["low", "medium", "high"]
    },
    "forbiddenClaims": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "complianceTips": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "needsHumanReview": {
      "type": "boolean"
    }
  }
}
```

### 3.2 示例

```json
{
  "category": "Home Cleaning Tool",
  "sellingPoints": [
    "Easy to use",
    "Cleans tile gaps",
    "Saves time"
  ],
  "painPoints": [
    "Dirty bathroom corners",
    "Hard-to-clean gaps"
  ],
  "targetAudience": [
    "home users",
    "renters",
    "cleaning lovers"
  ],
  "scenes": [
    "bathroom",
    "kitchen",
    "sink"
  ],
  "recommendedVideoTypes": [
    "pain_point_solution",
    "before_after",
    "review"
  ],
  "videoScore": 86,
  "riskTips": [
    "Avoid exaggerated cleaning claims"
  ],
  "claimRiskLevel": "low",
  "forbiddenClaims": [],
  "complianceTips": [
    "Avoid promising guaranteed cleaning results"
  ],
  "needsHumanReview": false
}
```

---

## 4. VideoPlanResult Schema

### 4.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/video-plan-result.schema.json",
  "title": "VideoPlanResult",
  "type": "object",
  "additionalProperties": false,
  "required": ["plans"],
  "properties": {
    "plans": {
      "type": "array",
      "minItems": 3,
      "maxItems": 5,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": [
          "type",
          "title",
          "hook",
          "structure",
          "reason",
          "estimatedDuration",
          "score"
        ],
        "properties": {
          "type": {
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
          "title": {
            "type": "string",
            "minLength": 1
          },
          "hook": {
            "type": "string",
            "minLength": 1,
            "maxLength": 120
          },
          "structure": {
            "type": "string",
            "minLength": 1
          },
          "reason": {
            "type": "string",
            "minLength": 1
          },
          "estimatedDuration": {
            "type": "integer",
            "minimum": 15,
            "maximum": 30
          },
          "score": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          }
        }
      }
    }
  }
}
```

### 4.2 示例

```json
{
  "plans": [
    {
      "type": "pain_point_solution",
      "title": "Pain Point Solution",
      "hook": "Still cleaning this by hand?",
      "structure": "Pain point → Product → Result → CTA",
      "reason": "The product solves a visible cleaning pain point.",
      "estimatedDuration": 22,
      "score": 88
    },
    {
      "type": "before_after",
      "title": "Before and After",
      "hook": "The before and after is so satisfying.",
      "structure": "Before → Product in use → After → CTA",
      "reason": "The cleaning result can be shown visually.",
      "estimatedDuration": 20,
      "score": 85
    },
    {
      "type": "review",
      "title": "Real Test Review",
      "hook": "I tested this cleaning tool at home.",
      "structure": "Question → Test → Result → Recommendation",
      "reason": "A review format makes the product feel more trustworthy.",
      "estimatedDuration": 25,
      "score": 82
    }
  ]
}
```

---

## 5. StoryboardResult Schema

### 5.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/storyboard-result.schema.json",
  "title": "StoryboardResult",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "title",
    "hook",
    "duration",
    "caption",
    "hashtags",
    "coverText",
    "musicSuggestion",
    "shots"
  ],
  "properties": {
    "title": {
      "type": "string",
      "minLength": 1,
      "maxLength": 120
    },
    "hook": {
      "type": "string",
      "minLength": 1,
      "maxLength": 120
    },
    "duration": {
      "type": "integer",
      "minimum": 15,
      "maximum": 30
    },
    "caption": {
      "type": "string",
      "minLength": 1,
      "maxLength": 500
    },
    "hashtags": {
      "type": "array",
      "minItems": 1,
      "maxItems": 10,
      "items": {
        "type": "string",
        "pattern": "^#[A-Za-z0-9_]+$"
      }
    },
    "coverText": {
      "type": "string",
      "minLength": 1,
      "maxLength": 80
    },
    "musicSuggestion": {
      "type": "string",
      "minLength": 1
    },
    "shots": {
      "type": "array",
      "minItems": 4,
      "maxItems": 12,
      "items": {
        "type": "object",
        "additionalProperties": false,
          "required": [
            "shotNo",
            "duration",
            "scene",
            "action",
            "subtitle",
            "materialType",
            "editInstruction"
          ],
        "properties": {
          "shotNo": {
            "type": "integer",
            "minimum": 1
          },
          "duration": {
            "type": "integer",
            "minimum": 1,
            "maximum": 8
          },
          "scene": {
            "type": "string",
            "minLength": 1
          },
          "action": {
            "type": "string"
          },
          "subtitle": {
            "type": "string",
            "minLength": 1,
            "maxLength": 90
          },
          "materialType": {
            "type": "string",
            "enum": [
              "product_image",
              "product_image_motion",
              "ai_image",
              "ai_video",
              "text_animation",
              "uploaded_video"
            ]
          },
          "prompt": {
            "type": "string",
            "minLength": 1
          },
          "negativePrompt": {
            "type": "string"
          },
          "editInstruction": {
            "type": "string"
          }
        },
        "allOf": [
          {
            "if": {
              "properties": {
                "materialType": {
                  "enum": ["ai_image", "ai_video"]
                }
              },
              "required": ["materialType"]
            },
            "then": {
              "required": ["prompt"]
            }
          }
        ]
      }
    }
  }
}
```

### 5.2 示例

```json
{
  "title": "This cleaning tool makes bathroom cleaning easier",
  "hook": "Still cleaning this by hand?",
  "duration": 22,
  "caption": "A simple tool that makes cleaning easier at home.",
  "hashtags": [
    "#cleaning",
    "#homehacks",
    "#tiktokshop"
  ],
  "coverText": "Clean smarter, not harder",
  "musicSuggestion": "Fast, satisfying cleaning-style background music",
  "shots": [
    {
      "shotNo": 1,
      "duration": 3,
      "scene": "Close-up of dirty bathroom tile gaps",
      "action": "Show the hard-to-clean corner",
      "subtitle": "Still cleaning this by hand?",
      "materialType": "ai_video",
      "prompt": "A realistic vertical TikTok-style shot of dirty bathroom tile gaps, natural home lighting, handheld phone camera feel",
      "negativePrompt": "deformed product, extra fingers, distorted hands, unreadable text, watermark",
      "editInstruction": "Quick cut, close-up, strong visual pain point"
    }
  ]
}
```

---

## 6. MaterialResult Schema

### 6.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/material-result.schema.json",
  "title": "MaterialResult",
  "type": "object",
  "additionalProperties": false,
  "required": ["materials"],
  "properties": {
    "materials": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": [
          "shotNo",
          "type",
          "provider",
          "status"
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
              "cover_image",
              "audio",
              "subtitle"
            ]
          },
          "url": {
            "type": "string",
            "format": "uri"
          },
          "prompt": {
            "type": "string"
          },
          "negativePrompt": {
            "type": "string"
          },
          "provider": {
            "type": "string"
          },
          "modelName": {
            "type": "string"
          },
          "source": {
            "type": "string",
            "enum": ["user_upload", "existing_asset", "ai_generated"]
          },
          "imagePurpose": {
            "type": "string",
            "enum": ["first_frame", "last_frame", "reference", "product_detail"]
          },
          "qualityScore": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          },
          "status": {
            "type": "string",
            "enum": [
              "completed",
              "failed"
            ]
          },
          "errorMessage": {
            "type": "string"
          }
        },
        "allOf": [
          {
            "if": {
              "properties": {
                "status": {
                  "const": "completed"
                }
              },
              "required": ["status"]
            },
            "then": {
              "required": ["url"]
            }
          },
          {
            "if": {
              "properties": {
                "status": {
                  "const": "failed"
                }
              },
              "required": ["status"]
            },
            "then": {
              "required": ["errorMessage"]
            }
          }
        ]
      }
    }
  }
}
```

### 6.2 示例

```json
{
  "materials": [
    {
      "shotNo": 1,
      "type": "video",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-clips/shot-1.mp4",
      "prompt": "A realistic vertical TikTok-style shot of dirty bathroom tile gaps...",
      "negativePrompt": "watermark, low quality, distorted hands",
      "provider": "video_model_provider",
      "modelName": "example-video-model",
      "qualityScore": 82,
      "status": "completed"
    }
  ]
}
```

---

## 7. QualityCheckResult Schema

### 7.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/quality-check-result.schema.json",
  "title": "QualityCheckResult",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "qualityScore",
    "riskScore",
    "checks",
    "complianceTips",
    "forbiddenClaims",
    "needsHumanReview",
    "suggestions"
  ],
  "properties": {
    "qualityScore": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "riskScore": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "checks": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "hasHook",
        "productAppearsEarly",
        "subtitleReadable",
        "noSensitiveClaims",
        "visualQualityAcceptable"
      ],
      "properties": {
        "hasHook": {
          "type": "boolean"
        },
        "productAppearsEarly": {
          "type": "boolean"
        },
        "subtitleReadable": {
          "type": "boolean"
        },
        "noSensitiveClaims": {
          "type": "boolean"
        },
        "visualQualityAcceptable": {
          "type": "boolean"
        }
      }
    },
    "complianceTips": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "forbiddenClaims": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "needsHumanReview": {
      "type": "boolean"
    },
    "suggestions": {
      "type": "array",
      "items": {
        "type": "string"
      }
    }
  }
}
```

### 7.2 示例

```json
{
  "qualityScore": 86,
  "riskScore": 12,
  "checks": {
    "hasHook": true,
    "productAppearsEarly": true,
    "subtitleReadable": true,
    "noSensitiveClaims": true,
    "visualQualityAcceptable": true
  },
  "complianceTips": [
    "Avoid guaranteed or medical-style claims"
  ],
  "forbiddenClaims": [],
  "needsHumanReview": false,
  "suggestions": [
    "The hook is clear.",
    "Consider making the product appear in the first 3 seconds."
  ]
}
```

---

## 8. AiCallbackPayload Schema

### 8.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/ai-callback-payload.schema.json",
  "title": "AiCallbackPayload",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "taskId",
    "schemaVersion",
    "stage",
    "status"
  ],
  "properties": {
    "taskId": {
      "type": "string",
      "format": "uuid"
    },
    "schemaVersion": {
      "type": "string",
      "const": "1.0.0"
    },
    "stage": {
      "type": "string",
      "enum": [
        "asset_analysis",
        "reference_analysis",
        "creative_plan",
        "product_analysis",
        "video_plan",
        "storyboard",
        "material",
        "quality_check",
        "render_manifest",
        "keyframe",
        "video_clip",
        "qa",
        "repair"
      ]
    },
    "status": {
      "type": "string",
      "enum": [
        "success",
        "failed"
      ]
    },
    "nextTaskStatus": {
      "type": "string",
      "enum": [
        "asset_uploading",
        "asset_analyzing",
        "reference_analyzing",
        "plan_generating",
        "analysis_completed",
        "plan_generated",
        "storyboard_generating",
        "script_generated",
        "material_generated",
        "checking",
        "rendering",
        "failed",
        "waiting_asset_confirmation",
        "waiting_plan_selection",
        "waiting_storyboard_confirmation",
        "keyframe_configuring",
        "image_generating",
        "waiting_image_confirmation",
        "video_clip_generating",
        "waiting_video_clip_confirmation",
        "waiting_final_review",
        "repairing",
        "completed",
        "cancelled"
      ]
    },
    "fashionAssetAnalysis": {
      "$ref": "fashion-asset-analysis.schema.json"
    },
    "referenceAnalysis": {
      "type": "object"
    },
    "productAnalysis": {
      "$ref": "product-analysis.schema.json"
    },
    "plans": {
      "$ref": "video-plan-result.schema.json#/properties/plans"
    },
    "storyboard": {
      "$ref": "storyboard-result.schema.json"
    },
    "materials": {
      "$ref": "material-result.schema.json#/properties/materials"
    },
    "qualityCheck": {
      "$ref": "quality-check-result.schema.json"
    },
    "renderManifest": {
      "type": "object"
    },
    "keyframes": {
      "$ref": "keyframe-generation-result.schema.json#/properties/keyframes"
    },
    "clips": {
      "$ref": "video-clip-generation-result.schema.json#/properties/clips"
    },
    "qaResult": {
      "$ref": "fashion-qa-result.schema.json"
    },
    "repairResult": {
      "$ref": "repair-result.schema.json"
    },
    "error": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "errorCode",
        "errorMessage",
        "failedStage",
        "retryable"
      ],
      "properties": {
        "errorCode": {
          "type": "string"
        },
        "errorMessage": {
          "type": "string"
        },
        "failedStage": {
          "type": "string"
        },
        "retryable": {
          "type": "boolean"
        },
        "provider": {
          "type": "string"
        },
        "rawError": {
          "type": "object",
          "additionalProperties": true
        }
      }
    }
  },
  "allOf": [
    {
      "if": {
        "properties": {
          "status": {
            "const": "failed"
          }
        },
        "required": ["status"]
      },
      "then": {
        "required": ["error"]
      }
    }
  ]
}
```

---

## 9. Python 校验建议

Python AI 服务建议使用：

```text
Pydantic Model + jsonschema 双重校验
```

处理流程：

```text
1. 模型返回 raw text。
2. 提取 JSON。
3. 用 jsonschema 校验。
4. 用 Pydantic 转成内部对象。
5. 校验失败则尝试修复。
6. 修复失败则重试。
7. 重试超过次数后回调 Java failed。
```

---

## 10. FashionAssetAnalysis Schema (Fashion Creative Loop)

### 10.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/fashion-asset-analysis.schema.json",
  "title": "FashionAssetAnalysis",
  "type": "object",
  "additionalProperties": false,
  "required": ["schemaVersion", "analysisText", "analyzedAssetIds", "model", "analyzedAt"],
  "properties": {
    "schemaVersion": { "const": "1.0" },
    "analysisText": { "type": "string", "minLength": 1 },
    "analyzedAssetIds": { "type": "array", "items": { "type": "string" } },
    "model": { "type": "string", "minLength": 1 },
    "analyzedAt": { "type": "string", "minLength": 1 }
  }
}
```

### 10.2 示例

```json
{
  "schemaVersion": "1.0",
  "analysisText": "素材展示蓝白碎花波西米亚连衣裙，最有价值的创作机会是围绕裙摆动态构建度假叙事。避免普通商品轮播、改变花纹，以及在缺少背面参考时虚构背面细节。",
  "analyzedAssetIds": ["asset-1", "asset-2"],
  "model": "vision-model",
  "analyzedAt": "2026-07-11T12:00:00Z"
}
```

方案与分镜生成统一接收运行时组装的 `creativeContext`：

```json
{
  "productProfile": {},
  "userRequest": { "rawPrompt": "用户输入原文", "parsed": {}, "confirmed": {} },
  "assetAnalysis": {},
  "workflow": { "taskMode": "PRODUCT_CREATIVE", "durationSeconds": 20, "videoType": "product_showcase" }
}
```

该对象不单独持久化；Java 在每次调用 AI 前从商品、用户需求、任务级素材分析和任务参数动态组装。

---

## 11. KeyframeGenerationResult Schema (Fashion Creative Loop)

### 11.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/keyframe-generation-result.schema.json",
  "title": "KeyframeGenerationResult",
  "type": "object",
  "additionalProperties": false,
  "required": ["keyframes"],
  "properties": {
    "keyframes": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["shotNo", "status"],
        "properties": {
          "shotNo": {
            "type": "integer",
            "minimum": 1
          },
          "status": {
            "type": "string",
            "enum": ["completed", "failed"]
          },
          "url": {
            "type": "string",
            "format": "uri"
          },
          "prompt": {
            "type": "string"
          },
          "negativePrompt": {
            "type": "string"
          },
          "provider": {
            "type": "string"
          },
          "modelName": {
            "type": "string"
          },
          "qualityScore": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          },
          "errorMessage": {
            "type": "string"
          }
        },
        "allOf": [
          {
            "if": {
              "properties": { "status": { "const": "completed" } },
              "required": ["status"]
            },
            "then": { "required": ["url"] }
          },
          {
            "if": {
              "properties": { "status": { "const": "failed" } },
              "required": ["status"]
            },
            "then": { "required": ["errorMessage"] }
          }
        ]
      }
    }
  }
}
```

### 11.2 示例

```json
{
  "keyframes": [
    {
      "shotNo": 1,
      "status": "completed",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-images/shot-1.png",
      "prompt": "Fashion keyframe: full front view of floral summer dress on model, beach background, golden hour lighting, 9:16 vertical, TikTok style",
      "negativePrompt": "deformed body, extra limbs, distorted face, watermark, low quality",
      "provider": "image_model_provider",
      "modelName": "example-image-model",
      "qualityScore": 88
    }
  ]
}
```

---

## 12. VideoClipGenerationResult Schema (Fashion Creative Loop)

### 12.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/video-clip-generation-result.schema.json",
  "title": "VideoClipGenerationResult",
  "type": "object",
  "additionalProperties": false,
  "required": ["clips"],
  "properties": {
    "clips": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["shotNo", "status"],
        "properties": {
          "shotNo": {
            "type": "integer",
            "minimum": 1
          },
          "status": {
            "type": "string",
            "enum": ["completed", "failed"]
          },
          "url": {
            "type": "string",
            "format": "uri"
          },
          "prompt": {
            "type": "string"
          },
          "negativePrompt": {
            "type": "string"
          },
          "provider": {
            "type": "string"
          },
          "modelName": {
            "type": "string"
          },
          "duration": {
            "type": "integer",
            "minimum": 1,
            "maximum": 8
          },
          "qualityScore": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          },
          "errorMessage": {
            "type": "string"
          }
        },
        "allOf": [
          {
            "if": {
              "properties": { "status": { "const": "completed" } },
              "required": ["status"]
            },
            "then": { "required": ["url", "duration"] }
          },
          {
            "if": {
              "properties": { "status": { "const": "failed" } },
              "required": ["status"]
            },
            "then": { "required": ["errorMessage"] }
          }
        ]
      }
    }
  }
}
```

### 12.2 示例

```json
{
  "clips": [
    {
      "shotNo": 1,
      "status": "completed",
      "url": "https://cos.example.com/users/u1/tasks/t1/ai-clips/shot-1.mp4",
      "prompt": "Fashion video: model walking on beach in floral summer dress, gentle breeze blowing fabric, 9:16 vertical, natural lighting, 3 seconds",
      "negativePrompt": "deformed body, extra limbs, distorted face, jittery motion, watermark",
      "provider": "video_model_provider",
      "modelName": "example-video-model",
      "duration": 3,
      "qualityScore": 82
    }
  ]
}
```

---

## 13. RepairResult Schema (Fashion Creative Loop)

### 13.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/repair-result.schema.json",
  "title": "RepairResult",
  "type": "object",
  "additionalProperties": false,
  "required": ["feedbackCategory", "targetType", "strategy", "affectedShots"],
  "properties": {
    "repairEventId": {
      "type": "string"
    },
    "feedbackCategory": {
      "type": "string",
      "enum": [
        "visual_quality",
        "product_accuracy",
        "lighting_issue",
        "action_stiffness",
        "missing_detail",
        "layout_composition",
        "style_mismatch",
        "other"
      ]
    },
    "targetType": {
      "type": "string",
      "enum": ["storyboard", "keyframe", "video_clip", "plan"]
    },
    "strategy": {
      "type": "string",
      "enum": [
        "rewrite_storyboard_shot",
        "regenerate_keyframe_prompt",
        "regenerate_keyframe",
        "regenerate_video_clip_prompt",
        "regenerate_video_clip",
        "adjust_edit_instruction",
        "reorder_shots"
      ]
    },
    "affectedShots": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "integer",
        "minimum": 1
      }
    },
    "repairNotes": {
      "type": "string"
    },
    "preserveConstraints": {
      "type": "object",
      "properties": {
        "productDetails": {
          "type": "array",
          "items": { "type": "string" }
        },
        "styleAttributes": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    },
    "newPrompt": {
      "type": "string"
    },
    "newStoryboardShot": {
      "type": "object"
    },
    "estimatedCostTier": {
      "type": "string",
      "enum": ["cheap", "moderate", "expensive"]
    },
    "requiresUserConfirmation": {
      "type": "boolean"
    }
  }
}
```

### 13.2 示例

```json
{
  "feedbackCategory": "missing_detail",
  "targetType": "keyframe",
  "strategy": "regenerate_keyframe_prompt",
  "affectedShots": [3],
  "repairNotes": "Back floral pattern detail not visible in current keyframe. Adjusting prompt to emphasize back view and pattern details.",
  "preserveConstraints": {
    "productDetails": ["floral print pattern", "A-line silhouette"],
    "styleAttributes": ["casual", "feminine"]
  },
  "newPrompt": "Fashion keyframe: back view of floral summer dress showing detailed blue floral pattern on white fabric, outdoor natural lighting, 9:16 vertical, TikTok style, fabric detail visible",
  "estimatedCostTier": "cheap",
  "requiresUserConfirmation": false
}
```

---

## 14. FashionQaResult Schema (Fashion Creative Loop)

### 14.1 Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tk-ai-video.local/schemas/fashion-qa-result.schema.json",
  "title": "FashionQaResult",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "stage",
    "qualityScore",
    "checks",
    "needsHumanReview"
  ],
  "properties": {
    "stage": {
      "type": "string",
      "enum": ["keyframe", "video_clip", "storyboard", "final_video"]
    },
    "qualityScore": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "riskScore": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100
    },
    "checks": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "productVisible",
        "styleAccurate",
        "lightingAcceptable",
        "compositionValid",
        "noArtifacts"
      ],
      "properties": {
        "productVisible": {
          "type": "boolean"
        },
        "styleAccurate": {
          "type": "boolean"
        },
        "lightingAcceptable": {
          "type": "boolean"
        },
        "compositionValid": {
          "type": "boolean"
        },
        "noArtifacts": {
          "type": "boolean"
        },
        "modelNatural": {
          "type": "boolean"
        },
        "fabricDetailVisible": {
          "type": "boolean"
        },
        "colorAccuracy": {
          "type": "boolean"
        }
      }
    },
    "suggestions": {
      "type": "array",
      "items": { "type": "string" }
    },
    "complianceTips": {
      "type": "array",
      "items": { "type": "string" }
    },
    "forbiddenClaims": {
      "type": "array",
      "items": { "type": "string" }
    },
    "needsHumanReview": {
      "type": "boolean"
    }
  }
}
```

### 14.2 示例

```json
{
  "stage": "keyframe",
  "qualityScore": 88,
  "riskScore": 10,
  "checks": {
    "productVisible": true,
    "styleAccurate": true,
    "lightingAcceptable": true,
    "compositionValid": true,
    "noArtifacts": true,
    "modelNatural": true,
    "fabricDetailVisible": true,
    "colorAccuracy": true
  },
  "suggestions": [
    "Keyframe composition is well-balanced.",
    "Fabric texture could be enhanced in close-up shots."
  ],
  "complianceTips": [
    "Ensure no exaggerated slimming claims in subtitles."
  ],
  "forbiddenClaims": [],
  "needsHumanReview": false
}
```

---

## 15. Fashion Creative Loop Callback Stage Contract

| Stage | Callback Direction | nextTaskStatus (success) | nextTaskStatus (failed) |
|---|---|---|---|
| `asset_analysis` | Python → Java | `waiting_asset_confirmation` | `failed` |
| `reference_analysis` | Python -> Java | `plan_generating` | `failed` |
| `creative_plan` | Python → Java | `waiting_plan_selection` | `failed` |
| `product_analysis` | Python → Java | `analysis_completed` | `failed` |
| `video_plan` | Python → Java | `plan_generated` | `failed` |
| `storyboard` | Python → Java | `script_generated` | `failed` |
| `material` | Python → Java | `material_generated` | `failed` |
| `quality_check` | Python → Java | `checking` | `failed` |
| `render_manifest` | Python → Java | `rendering` | `failed` |
| `keyframe` | Python → Java | `waiting_image_confirmation` | `failed` |
| `video_clip` | Python → Java | `waiting_video_clip_confirmation` | `failed` |
| `qa` | Python → Java | (depends on QA target) | `failed` |
| `repair` | Python -> Java | target-specific state (`image_generating` / `video_clip_generating` / `rendering` / `keyframe_configuring`) | `failed` |

Callback payload fields per stage:

| Stage | Required Payload Field |
|---|---|
| `asset_analysis` | `fashionAssetAnalysis` |
| `reference_analysis` | `referenceAnalysis` |
| `creative_plan` | `plans` |
| `product_analysis` | `productAnalysis` |
| `video_plan` | `plans` |
| `storyboard` | `storyboard` |
| `material` | `materials` |
| `quality_check` | `qualityCheck` |
| `render_manifest` | `renderManifest` |
| `keyframe` | `keyframes` |
| `video_clip` | `clips` |
| `qa` | `qaResult` |
| `repair` | `repairResult` |

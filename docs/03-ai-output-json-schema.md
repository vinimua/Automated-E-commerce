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
        "product_analysis",
        "video_plan",
        "storyboard",
        "material",
        "quality_check",
        "render_manifest"
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
        "analysis_completed",
        "plan_generated",
        "script_generated",
        "material_generated",
        "checking",
        "rendering",
        "failed"
      ]
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

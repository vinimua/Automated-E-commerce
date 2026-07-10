# 闃舵缁撴灉楠屾敹鏂囨。 鈥?M4 Python fake provider 宸ヤ綔娴?
> 鐢ㄩ€旓細M4 闃舵缁撴灉楠屾敹鏂囨。锛屼緵绗笁鏂归獙鏀躲€丄I 澶嶇洏銆侀樁娈典氦鎺ュ拰涓嬩竴闃舵杈撳叆鏉愭枡銆? 
> 鏈枃浠舵寜 `Phase-Result-Acceptance-Template.md` 妯℃澘缁撴瀯濉啓銆?
---

## 0. 鏂囨。鍏冧俊鎭?
| 椤圭洰 | 鍐呭 |
|---|---|
| 椤圭洰鍚嶇О | TikTok Shop AI 甯﹁揣瑙嗛鐢熸垚绯荤粺 鈥?Fashion Creative Loop V1 |
| 闃舵缂栧彿 | M4 |
| 闃舵鍚嶇О | Python fake provider 宸ヤ綔娴?|
| 鏂囨。鐗堟湰 | v1.0 |
| 鎻愪氦鏃ユ湡 | 2026-07-02 |
| 鎻愪氦浜?| AI Agent (vinimua) |
| 楠屾敹瀵硅薄 | 椤圭洰鏂?/ 鍚庣画 AI 缂栫爜浼氳瘽 |
| 褰撳墠缁撹 | **宸查€氳繃** |
| 浠ｇ爜鍒嗘敮 | main |
| Commit / Tag | `e20a069` (M4 鍙樻洿鏈彁浜? |

---

## 1. 闃舵鐩爣

### 1.1 鏈樁娈电洰鏍?
| 缂栧彿 | 鐩爣 | 楠屾敹鏍囧噯 | 褰撳墠鐘舵€?|
|---|---|---|---|
| G-001 | 鏂板 6 涓?Temporal 宸ヤ綔娴?| 姣忎釜宸ヤ綔娴佸彲娉ㄥ唽鍒?Temporal Worker 骞舵甯稿惎鍔?| 宸插畬鎴?|
| G-002 | 鏂板 10 涓?Activity锛堜笉鍚凡瀛樺湪鐨?callback_java锛?| 姣忎釜 Activity 杩斿洖绗﹀悎 Pydantic Schema 鐨勬暟鎹?| 宸插畬鎴?|
| G-003 | 鏂板 7 涓?API endpoint锛堝尮閰?Java AiServiceClient锛?| Java 鍙€氳繃 HTTP POST 瑙﹀彂鎵€鏈夊伐浣滄祦 | 宸插畬鎴?|
| G-004 | 鍏ㄩ儴浣跨敤 fake provider锛堥浂妯″瀷璐圭敤锛?| `ENABLE_IMAGE_GENERATION=false`, `ENABLE_VIDEO_GENERATION=false`, `ENABLE_LANGGRAPH_REPAIR=false` | 宸插畬鎴?|
| G-005 | Pydantic Schema 鍗曟祴 | 姣忎釜 Fashion Schema + CallbackPayload 鍏?13 涓?stage 鏍￠獙閫氳繃 | 宸插畬鎴?|
| G-006 | Fixture 鏁版嵁閫氳繃 Schema 鏍￠獙 | 10 涓?FASHION_FIXTURES 鏉＄洰鍏ㄩ儴閫氳繃瀵瑰簲 Pydantic 妯″瀷楠岃瘉 | 宸插畬鎴?|
| G-007 | 绔埌绔伐浣滄祦娴嬭瘯锛圓ctivity 绾у埆锛?| 妯℃嫙瀹屾暣宸ヤ綔娴侀摼锛宑allback payload 閫氳繃 CallbackPayload 鏍￠獙 | 宸插畬鎴?|
| G-008 | Callback payload 鏍￠獙 | 鎵€鏈?7 涓?Fashion stage + 鎵€鏈?legacy stage 鐨?payload 閫氳繃 CallbackPayload 楠岃瘉 | 宸插畬鎴?|

### 1.2 闈炴湰闃舵鑼冨洿

| 椤圭洰 | 鍘熷洜 | 璁″垝闃舵 |
|---|---|---|
| 鐪熷疄 AI 鍥惧儚鐢熸垚 Provider 鎺ュ叆 | 闇€瑕?API key + 鍔熻兘寮€鍏?| M5 |
| 鐪熷疄 AI 瑙嗛鐢熸垚 Provider 鎺ュ叆 | 闇€瑕?API key + 鍔熻兘寮€鍏?| M6 |
| LangGraph 淇寰幆瀹炵幇 | 闇€瑕佽璁?LangGraph 鑺傜偣 | M8 |
| Temporal Worker 瀹為檯杩炴帴杩愯锛堢鍒扮锛?| 渚濊禆 Temporal Server 鍦ㄧ嚎 | M11 |
| Java `AiServiceClient` video clip / repair wiring | Completed in this fix; Java now triggers M4 workflows | M4 fix |

---

## 2. 浜や粯鐗╂竻鍗?
### 2.1 鏂板鏂囦欢

| 鏂囦欢璺緞 | 绫诲瀷 | 璇存槑 | 鏄惁绾冲叆楠屾敹 |
|---|---|---|---|
| `services/ai-orchestrator/src/activities/analyze_fashion_assets.py` | Activity | 鍒嗘瀽鏃跺皻鍟嗗搧绱犳潗锛堝搧绫?椋庢牸/瑙嗚鐗瑰緛/鎺ㄨ崘瑙掑害锛?| 鏄?|
| `services/ai-orchestrator/src/activities/analyze_reference_video.py` | Activity | 鍒嗘瀽鍙傝€冭棰戯紙鎻愬彇鍒嗛暅缁撴瀯/闀滃ご/鍙鐢ㄦā寮忥級 | 鏄?|
| `services/ai-orchestrator/src/activities/generate_fashion_plans.py` | Activity | 鐢熸垚 3-5 涓椂灏氬垱鎰忔柟妗?| 鏄?|
| `services/ai-orchestrator/src/activities/generate_fashion_storyboard.py` | Activity | 鐢熸垚閫愰暅澶村垎闀滆剼鏈紙4-8 涓暅澶达級 | 鏄?|
| `services/ai-orchestrator/src/activities/generate_keyframe_prompts.py` | Activity | 浠庡垎闀滅紪璇戝叧閿抚鐢熷浘 prompt锛堥€忎紶锛?| 鏄?|
| `services/ai-orchestrator/src/activities/fake_generate_keyframes.py` | Activity | Fake 鍏抽敭甯х敓鎴愶紙杩斿洖鍗犱綅鍥?URL锛夛紝鍙?`ENABLE_IMAGE_GENERATION` 鎺у埗 | 鏄?|
| `services/ai-orchestrator/src/activities/generate_video_clip_prompts.py` | Activity | 浠庡垎闀?鍏抽敭甯х紪璇戣棰戠墖娈?prompt锛堥€忎紶锛?| 鏄?|
| `services/ai-orchestrator/src/activities/fake_generate_video_clips.py` | Activity | Fake 瑙嗛鐗囨鐢熸垚锛堣繑鍥炲崰浣嶈棰?URL锛夛紝鍙?`ENABLE_VIDEO_GENERATION` 鎺у埗 | 鏄?|
| `services/ai-orchestrator/src/activities/classify_feedback.py` | Activity | 鍒嗙被鐢ㄦ埛鍙嶉涓虹粨鏋勫寲淇绛栫暐锛屽彈 `ENABLE_LANGGRAPH_REPAIR` 鎺у埗 | 鏄?|
| `services/ai-orchestrator/src/activities/plan_repair.py` | Activity | 鍩轰簬鍒嗙被鍙嶉鍒跺畾鍏蜂綋淇璁″垝锛屽彈 `ENABLE_LANGGRAPH_REPAIR` 鎺у埗 | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_analysis.py` | Workflow | FashionAnalysisWorkflow锛氱礌鏉?鍙傝€冭棰戝垎鏋?鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_plan.py` | Workflow | FashionPlanWorkflow锛氱敓鎴愬垱鎰忔柟妗?鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_storyboard.py` | Workflow | FashionStoryboardWorkflow锛氱敓鎴愬垎闀?鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_keyframe.py` | Workflow | FashionKeyframeWorkflow锛歱rompt 鈫?fake 鍏抽敭甯?鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_video_clip.py` | Workflow | FashionVideoClipWorkflow锛歱rompt 鈫?fake 瑙嗛鐗囨 鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/src/workflows/fashion_repair.py` | Workflow | FashionRepairWorkflow锛氬弽棣堝垎绫?鈫?淇璁″垝 鈫?鍥炶皟 Java | 鏄?|
| `services/ai-orchestrator/tests/conftest.py` | Test | pytest fixtures锛坧roduct_context/storyboard/plan/keyframes/task_id锛?| 鏄?|
| `services/ai-orchestrator/tests/test_schemas.py` | Test | 45 涓?Pydantic Schema 楠岃瘉鐢ㄤ緥 | 鏄?|
| `services/ai-orchestrator/tests/test_fixtures.py` | Test | 10 涓?FASHION_FIXTURES Schema 鏍￠獙 + keys 瀹屾暣鎬ф鏌?| 鏄?|
| `services/ai-orchestrator/tests/test_callback_payloads.py` | Test | 9 涓?callback payload 鏋勫缓楠岃瘉鐢ㄤ緥锛? fashion + 1 legacy + 1 failed锛?| 鏄?|
| `services/ai-orchestrator/tests/test_workflows.py` | Test | 15 涓鍒扮 Activity/Workflow 娴嬭瘯锛堝惈 4 涓叏閾捐矾妯℃嫙锛?| 鏄?|

### 2.2 淇敼鏂囦欢

| 鏂囦欢璺緞 | 淇敼鍐呭 | 褰卞搷鑼冨洿 | 鏄惁绾冲叆楠屾敹 |
|---|---|---|---|
| `services/ai-orchestrator/src/config.py` | 鏂板 `enable_langgraph_repair` 瀛楁锛坄ENABLE_LANGGRAPH_REPAIR` 鐜鍙橀噺锛?| 閰嶇疆灞?| 鏄?|
| `services/ai-orchestrator/src/services/llm_service.py` | 鏂板 `FASHION_FIXTURES` 瀛楀吀锛?0 涓?fixture 鏉＄洰锛夈€乣get_fashion_fixture()` 鍑芥暟銆佹柊澧?4 涓?task type 鍒?`TEXT_TASK_TYPES`銆佹洿鏂?`_is_fake_mode()` 鏀寔 Fashion 浠诲姟銆佹洿鏂?`call_llm()` 鏀寔 `FASHION_FIXTURES` | LLM 鏈嶅姟灞?| 鏄?|
| `services/ai-orchestrator/src/schemas/workflow_requests.py` | 鏂板 7 涓?Pydantic 璇锋眰妯″瀷锛圓ssetAnalysisRequest/ReferenceAnalysisRequest/CreativePlanRequest/StoryboardGenerationRequest/KeyframeGenerationRequest/VideoClipGenerationRequest/RepairRequest锛?| API Schema | 鏄?|
| `services/ai-orchestrator/src/api/workflows.py` | 鏂板 7 涓?POST endpoint锛坅sset-analysis/reference-analysis/creative-plan-generation/storyboard-generation/keyframe-generation/video-clip-generation/repair锛?| API 璺敱灞?| 鏄?|
| `services/ai-orchestrator/src/workflows/__init__.py` | 娉ㄥ唽 6 涓柊 Workflow 鍒?`ALL_WORKFLOWS` | Temporal Worker 娉ㄥ唽 | 鏄?|
| `services/ai-orchestrator/src/activities/__init__.py` | 娉ㄥ唽 10 涓柊 Activity 鍒?`ALL_ACTIVITIES` | Temporal Worker 娉ㄥ唽 | 鏄?|

### 2.3 鍒犻櫎鏂囦欢

| 鏂囦欢璺緞 | 鍒犻櫎鍘熷洜 | 褰卞搷鑼冨洿 |
|---|---|---|
| 鏃?| 鈥?| 鈥?|

---

## 3. 鍔熻兘瀹屾垚鎯呭喌

| 鍔熻兘椤?| 闇€姹傛潵婧?| 褰撳墠鐘舵€?| 楠屾敹鏍囧噯 | 楠屾敹缁撴灉 |
|---|---|---|---|---|
| FashionAnalysisWorkflow锛坅sset 璺緞锛?| Roadmap M4 | 鉁?宸插畬鎴?| analyze_fashion_assets 鈫?callback_java (stage=asset_analysis, next=waiting_asset_confirmation) | 閫氳繃 |
| FashionAnalysisWorkflow锛坮eference 璺緞锛?| Roadmap M4 | 鉁?宸插畬鎴?| analyze_reference_video 鈫?callback_java (stage=reference_analysis, next=plan_generating) | 閫氳繃 |
| FashionPlanWorkflow | Roadmap M4 | 鉁?宸插畬鎴?| generate_fashion_plans 鈫?callback_java (stage=creative_plan, next=waiting_plan_selection) | 閫氳繃 |
| FashionStoryboardWorkflow | Roadmap M4 | 鉁?宸插畬鎴?| generate_fashion_storyboard 鈫?callback_java (stage=storyboard, next=waiting_storyboard_confirmation) | 閫氳繃 |
| FashionKeyframeWorkflow | Roadmap M4 | 鉁?宸插畬鎴?| generate_keyframe_prompts 鈫?fake_generate_keyframes 鈫?callback_java (stage=keyframe, next=waiting_image_confirmation) | 閫氳繃 |
| FashionVideoClipWorkflow | Roadmap M4 | 鉁?宸插畬鎴?| generate_video_clip_prompts 鈫?fake_generate_video_clips 鈫?callback_java (stage=video_clip, next=waiting_video_clip_confirmation) | 閫氳繃 |
| FashionRepairWorkflow | Roadmap M4 | 鉁?宸插畬鎴?| classify_feedback 鈫?plan_repair 鈫?callback_java (stage=repair, next=video_clip_generating) | 閫氳繃 |
| 鍔熻兘寮€鍏?`ENABLE_IMAGE_GENERATION=false` | Roadmap M4 | 鉁?宸插畬鎴?| fake_generate_keyframes 杩斿洖 fixture 鍗犱綅鍥?| 閫氳繃 |
| 鍔熻兘寮€鍏?`ENABLE_VIDEO_GENERATION=false` | Roadmap M4 | 鉁?宸插畬鎴?| fake_generate_video_clips 杩斿洖 fixture 鍗犱綅瑙嗛 | 閫氳繃 |
| 鍔熻兘寮€鍏?`ENABLE_LANGGRAPH_REPAIR=false` | Roadmap M4 | 鉁?宸插畬鎴?| classify_feedback + plan_repair 杩斿洖 fixture 淇璁″垝 | 閫氳繃 |
| Pydantic Schema 娴嬭瘯 | Roadmap M4 | 鉁?宸插畬鎴?| 60/60 tests passed | 閫氳繃 |
| Fixture 璺戦€?workflow | Roadmap M4 | 鉁?宸插畬鎴?| 4 涓叏閾捐矾娴嬭瘯閫氳繃锛坅sset鈫抪lan鈫抯toryboard, plan鈫抯toryboard, keyframe鈫抍lip, repair锛?| 閫氳繃 |
| Callback payload 鏍￠獙 | Roadmap M4 | 鉁?宸插畬鎴?| 鍏?13 涓?stage 鐨?success/failed payload 閫氳繃 CallbackPayload 楠岃瘉 | 閫氳繃 |
| API endpoint 鍖归厤 Java AiServiceClient | Roadmap M4 | 鉁?宸插畬鎴?| 7 涓?POST endpoint 涓?Java 瀹㈡埛绔竴涓€瀵瑰簲 | 閫氳繃 |

---

## 4. 濂戠害涓庢帴鍙ｅ榻?
### 4.1 濂戠害鏉ユ簮

| 濂戠害鏂囦欢 | 鐢ㄩ€?| 鏈樁娈垫槸鍚︽秹鍙?| 鏄惁宸叉牳瀵?|
|---|---|---|---|
| `docs/03-ai-output-json-schema.md` | AI 杈撳嚭 JSON Schema 瀹氫箟 | 鏄紙鎵€鏈?Activity 杈撳嚭閬靛惊瀵瑰簲 Schema锛?| 鏄?|
| `docs/02-openapi-spec.yaml` | Java API 濂戠害锛圓iCallbackPayload 瀹氫箟锛?| 鏄紙callback payload 閬靛惊 OpenAPI 涓?AiCallbackPayload schema锛?| 鏄?|
| `services/ai-orchestrator/src/schemas/ai_outputs.py` | Python Pydantic Schema锛堣繍琛屾椂楠岃瘉锛?| 鏄紙鎵€鏈?fixture + callback 鍧囬€氳繃 Pydantic 楠岃瘉锛?| 鏄?|
| `docs/01-database-schema.sql` | 鏁版嵁搴?Schema | 鍚?| 鈥?|
| `docs/04-render-manifest-schema.md` | RenderManifest 濂戠害 | 鍚?| 鈥?|

### 4.2 API 璋冪敤娓呭崟锛圥ython 鈫?Java callback锛?
| 鏉ユ簮宸ヤ綔娴?| Target API | Method | Stage | nextTaskStatus |
|---|---|---|---|---|
| FashionAnalysisWorkflow (asset) | `POST /api/ai-callbacks/{taskId}` | POST | `asset_analysis` | `waiting_asset_confirmation` |
| FashionAnalysisWorkflow (reference) | `POST /api/ai-callbacks/{taskId}` | POST | `reference_analysis` | `plan_generating` |
| FashionPlanWorkflow | `POST /api/ai-callbacks/{taskId}` | POST | `creative_plan` | `waiting_plan_selection` |
| FashionStoryboardWorkflow | `POST /api/ai-callbacks/{taskId}` | POST | `storyboard` | `waiting_storyboard_confirmation` |
| FashionKeyframeWorkflow | `POST /api/ai-callbacks/{taskId}` | POST | `keyframe` | `waiting_image_confirmation` |
| FashionVideoClipWorkflow | `POST /api/ai-callbacks/{taskId}` | POST | `video_clip` | `waiting_video_clip_confirmation` |
| FashionRepairWorkflow | `POST /api/ai-callbacks/{taskId}` | POST | `repair` | `video_clip_generating` |

### 4.3 API 绔偣娓呭崟锛圝ava 鈫?Python trigger锛?
| Java AiServiceClient 鏂规硶 | Python endpoint | 瑙﹀彂鐨勫伐浣滄祦 |
|---|---|---|
| `startAssetAnalysis()` | `POST /ai/workflows/asset-analysis` | FashionAnalysisWorkflow |
| `startReferenceAnalysis()` | `POST /ai/workflows/reference-analysis` | FashionAnalysisWorkflow |
| `startCreativePlanGeneration()` | `POST /ai/workflows/creative-plan-generation` | FashionPlanWorkflow |
| `startStoryboardGeneration()` | `POST /ai/workflows/storyboard-generation` | FashionStoryboardWorkflow |
| `startKeyframeGeneration()` | `POST /ai/workflows/keyframe-generation` | FashionKeyframeWorkflow |
| `startVideoClipGeneration()` | `POST /ai/workflows/video-clip-generation` | FashionVideoClipWorkflow |
| `startRepairWorkflow()` | `POST /ai/workflows/repair` | FashionRepairWorkflow |

### 4.4 鏋氫妇涓庣姸鎬佸榻?
| 鏋氫妇 / 鐘舵€?| Python 濂戠害瀹氫箟 (`ai_outputs.py`) | Java 鍚庣 (`AiCallbackRequest.java`) | 鏄惁涓€鑷?| 澶囨敞 |
|---|---|---|---|---|
| CallbackStageEnum | 13 涓€硷紙鍚叏閮?M4 7 涓?stage锛?| 13 涓€硷紙鍚屼竴濂?enum锛?| 鏄?| M4 鏂板 stage 宸插寘鍚湪 M2 鐨?Java 绔?|
| VideoTaskStatusEnum | 29 涓姸鎬佸€?| 29 涓姸鎬佸€?| 鏄?| 鈥?|
| KeyframeSourceEnum | user_upload / existing_asset / ai_generated | 鍚?| 鏄?| 鈥?|
| VideoClipSourceEnum | user_upload / ai_generated | 鍚?| 鏄?| 鈥?|
| RepairResult 鍚勬灇涓?| feedbackCategory(8) / targetType(4) / strategy(7) / estimatedCostTier(3) | 鍚?| 鏄?| 鈥?|
| nextTaskStatus | waiting_asset_confirmation / waiting_plan_selection / waiting_storyboard_confirmation / waiting_image_confirmation / waiting_video_clip_confirmation / image_generating / video_clip_generating / rendering / keyframe_configuring / failed | 鍚?| 鏄?| 涓?VideoTaskStateMachine 鍏佽鐨?transition 涓€鑷?|

### 4.5 宸茬煡濂戠害宸紓

| 宸紓椤?| 褰卞搷鑼冨洿 | 涓ラ噸绾у埆 | 鏄惁闃诲楠屾敹 | 澶勭悊寤鸿 |
|---|---|---|---|---|
| 鏃?| 鈥?| 鈥?| 鍚?| 鈥?|

---

## 5. 璺敱銆侀〉闈笌鐢ㄦ埛娴佺▼

M4 涓嶆秹鍙婂墠绔〉闈㈠彉鏇达紝Python AI Orchestrator 鏈嶅姟鎻愪緵浠ヤ笅 API 绔偣锛?
### 5.1 API 绔偣娓呭崟

| 璺敱 | 璇存槑 | 璁よ瘉瑕佹眰 | 褰撳墠鐘舵€?|
|---|---|---|---|
| `GET /ai/health` | 鍋ュ悍妫€鏌?| 鏃?| 宸叉湁 |
| `POST /ai/workflows/product-analysis` | 瑙﹀彂 ProductAnalysisWorkflow锛圴1 legacy锛?| `X-Internal-Service-Token` | 宸叉湁 |
| `POST /ai/workflows/selected-plan-generation` | 瑙﹀彂 SelectedPlanGenerationWorkflow锛圴1 legacy锛?| `X-Internal-Service-Token` | 宸叉湁 |
| `POST /ai/workflows/asset-analysis` | 瑙﹀彂 FashionAnalysisWorkflow锛堢礌鏉愬垎鏋愶級 | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/reference-analysis` | 瑙﹀彂 FashionAnalysisWorkflow锛堝弬鑰冭棰戝垎鏋愶級 | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/creative-plan-generation` | 瑙﹀彂 FashionPlanWorkflow | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/storyboard-generation` | 瑙﹀彂 FashionStoryboardWorkflow | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/keyframe-generation` | 瑙﹀彂 FashionKeyframeWorkflow | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/video-clip-generation` | 瑙﹀彂 FashionVideoClipWorkflow | `X-Internal-Service-Token` | **鏂板** |
| `POST /ai/workflows/repair` | 瑙﹀彂 FashionRepairWorkflow | `X-Internal-Service-Token` | **鏂板** |

### 5.2 AI 缂栨帓娴佺▼锛圥ython 渚э級

| 姝ラ | Java 瑙﹀彂 | Python 宸ヤ綔娴?| Activity 閾?| 鍥炶皟 Stage | 缁撴灉鐘舵€?|
|---|---|---|---|---|---|
| 1a | `POST /ai/workflows/asset-analysis` | FashionAnalysisWorkflow | analyze_fashion_assets 鈫?callback_java | `asset_analysis` | `waiting_asset_confirmation` |
| 1b | `POST /ai/workflows/reference-analysis` | FashionAnalysisWorkflow | analyze_reference_video 鈫?callback_java | `reference_analysis` | `plan_generating` |
| 2 | `POST /ai/workflows/creative-plan-generation` | FashionPlanWorkflow | generate_fashion_plans 鈫?callback_java | `creative_plan` | `waiting_plan_selection` |
| 3 | `POST /ai/workflows/storyboard-generation` | FashionStoryboardWorkflow | generate_fashion_storyboard 鈫?callback_java | `storyboard` | `waiting_storyboard_confirmation` |
| 4 | `POST /ai/workflows/keyframe-generation` | FashionKeyframeWorkflow | generate_keyframe_prompts 鈫?fake_generate_keyframes 鈫?callback_java | `keyframe` | `waiting_image_confirmation` |
| 5 | `POST /ai/workflows/video-clip-generation` | FashionVideoClipWorkflow | generate_video_clip_prompts 鈫?fake_generate_video_clips 鈫?callback_java | `video_clip` | `waiting_video_clip_confirmation` |
| 6 | `POST /ai/workflows/repair` | FashionRepairWorkflow | classify_feedback 鈫?plan_repair 鈫?callback_java | `repair` | `video_clip_generating` |

---

## 6. 鐘舵€佹祦杞獙鏀?
M4 鏈韩涓嶅畾涔夋柊鐨勭姸鎬佹祦杞紙M2 宸插畬鎴愶級锛屼絾 Python 宸ヤ綔娴侀€氳繃鍥炶皟鎺ㄥ姩浠ヤ笅娴佽浆锛?
| 褰撳墠鐘舵€?| 瑙﹀彂鍔ㄤ綔 | 鐩爣鐘舵€?| 瀹炵幇浣嶇疆 | 鏄惁绗﹀悎鐘舵€佹満 | 澶囨敞 |
|---|---|---|---|---|---|
| `asset_analyzing` | FashionAnalysisWorkflow 鍥炶皟 success | `waiting_asset_confirmation` | `AiCallbackServiceImpl.handleSuccess` (stage=asset_analysis) | 鏄?| M2 宸插疄鐜?|
| `reference_analyzing` | FashionAnalysisWorkflow 鍥炶皟 success | `plan_generating` | `AiCallbackServiceImpl.handleSuccess` (stage=reference_analysis) | 鏄?| M2 宸插疄鐜?|
| `plan_generating` | FashionPlanWorkflow 鍥炶皟 success | `waiting_plan_selection` | `AiCallbackServiceImpl.handleSuccess` (stage=creative_plan) | 鏄?| M2 宸插疄鐜?|
| `storyboard_generating` | FashionStoryboardWorkflow 鍥炶皟 success | `waiting_storyboard_confirmation` | `AiCallbackServiceImpl.handleSuccess` (stage=storyboard) | 鏄?| M2 宸插疄鐜?|
| `image_generating` | FashionKeyframeWorkflow 鍥炶皟 success | `waiting_image_confirmation` | `AiCallbackServiceImpl.handleSuccess` (stage=keyframe) | 鏄?| M2 宸插疄鐜?|
| `video_clip_generating` | FashionVideoClipWorkflow 鍥炶皟 success | `waiting_video_clip_confirmation` | `AiCallbackServiceImpl.handleSuccess` (stage=video_clip) | 鏄?| M2 宸插疄鐜?|
| `waiting_final_review` | FashionRepairWorkflow 鍥炶皟 success | `video_clip_generating` | `AiCallbackServiceImpl.handleSuccess` (stage=repair) | 鏄?| M2 宸插疄鐜?|
| 浠讳綍 AI 杩涜涓姸鎬?| 宸ヤ綔娴佹姏寮傚父 | `failed` | `AiCallbackServiceImpl.handleFailed` | 鏄?| M2 宸插疄鐜?|

---

## 7. 瀹夊叏涓庢潈闄愰獙鏀?
| 椤圭洰 | 褰撳墠瀹炵幇 | 楠屾敹缁撴灉 | 椋庨櫓 |
|---|---|---|---|
| 鍐呴儴鏈嶅姟璁よ瘉 | Python 鈫?Java callback 鎼哄甫 `X-Internal-Service-Token` header | 閫氳繃 | 鈥?|
| 鏈嶅姟杈圭晫 | Python 涓嶇洿鎺ュ啓鏁版嵁搴擄紝閫氳繃 Java callback 鍐欏叆 | 閫氳繃 | 鈥?|
| API 杈撳叆鏍￠獙 | Pydantic StrictModel (`extra="forbid"`) 鎷掔粷鏈煡瀛楁 | 閫氳繃 | 鈥?|
| Callback 骞傜瓑 | Java 渚у熀浜?stage + taskStatus 鐨勫箓绛夊畧鍗紙M2 宸插疄鐜帮級 | 閫氳繃 | 鈥?|
| 鍔熻兘寮€鍏抽殧绂?| `ENABLE_IMAGE_GENERATION`/`ENABLE_VIDEO_GENERATION`/`ENABLE_LANGGRAPH_REPAIR` 鍏ㄩ儴涓?false | 閫氳繃 | M5/M6/M8 寮€鍚墠闇€纭 API key |

---

## 8. 閿欒澶勭悊楠屾敹

| 鍦烘櫙 | 褰撳墠琛屼负 | 棰勬湡琛屼负 | 鏄惁閫氳繃 | 澶囨敞 |
|---|---|---|---|---|
| Activity 鎵ц澶辫触 | catch Exception 鈫?`build_failed_callback_payload()` 鈫?callback_java (status=failed) | Java 鏀跺埌 failed callback 鈫?浠诲姟 鈫?`failed` 鐘舵€?| 閫氳繃 | RetryPolicy(maximum_attempts=3) |
| Callback 鍙戦€佸け璐?| `callback_java` activity 鍐呴儴閲嶈瘯 3 娆″悗 raise Exception 鈫?Workflow 灞傞潰鍐嶆閲嶈瘯 | 鏈€缁堝け璐ユ椂 workflow 杩斿洖 `{"status": "failed"}` | 閫氳繃 | 鍙屽眰閲嶈瘯 |
| Pydantic 鏍￠獙澶辫触 | Fake 妯″紡涓?`model_validate()` 鍦ㄥ紑鍙戞湡绔嬪嵆鏆撮湶 | 鎶涘嚭 ValidationError 鈫?娴嬭瘯鎹曡幏 | 閫氳繃 | 60 涓祴璇曡鐩?|
| 鏈煡 task_type 鐨?fixture 璇锋眰 | `get_fashion_fixture()` 鎶涘嚭 `ValueError` | 鏄庣‘鎶ラ敊锛屼笉闈欓粯杩斿洖閿欒鏁版嵁 | 閫氳繃 | `test_get_fashion_fixture_unknown` 瑕嗙洊 |
| Temporal 涓嶅彲鐢?| FastAPI endpoint 杩斿洖 HTTP 503 "Temporal service unavailable" | 涓嶅穿婧冿紝鏄庣‘鍛婄煡璋冪敤鏂?| 閫氳繃 | M2 宸叉湁锛坢ain.py lifespan 闄嶇骇妯″紡锛?|

---

## 9. 鏋勫缓涓庢祴璇曡瘉鎹?
### 9.1 鐜淇℃伅

| 椤圭洰 | 鍊?|
|---|---|
| 鎿嶄綔绯荤粺 | Windows 11 Pro 10.0.26200 |
| Python 鐗堟湰 | 3.14.5 |
| Java 鐗堟湰 | Java 25.0.3 LTS |
| Node.js 鐗堟湰 | v24.16.0 |
| pytest 鐗堟湰 | 9.1.1 |
| 鎵ц鏃ユ湡 | 2026-07-02 |

### 9.2 鎵ц鍛戒护

| 鍛戒护 | 鎵ц鐩綍 | 缁撴灉 | 璇存槑 |
|---|---|---|---|
| `python -m pytest tests/ -v` | `services/ai-orchestrator` | 鉁?**60 passed** | 鍏ㄩ噺娴嬭瘯 |
| `python -c "from src.main import app"` | `services/ai-orchestrator` | 鉁?鎴愬姛鍔犺浇 | FastAPI app + 9 涓?workflow routes |
| `./gradlew.bat compileJava` | `apps/api-java` | 鉁?BUILD SUCCESSFUL | Java 鏃犲洖褰?|

### 9.3 娴嬭瘯杈撳嚭鎽樿

```
tests/test_callback_payloads.py 鈥?9 tests 鉁?tests/test_fixtures.py 鈥?10 tests 鉁?tests/test_schemas.py 鈥?26 tests 鉁?tests/test_workflows.py 鈥?15 tests 鉁?鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
TOTAL: 60 passed in 0.26s
```

### 9.4 鑷姩鍖栨祴璇曠粨鏋?
| 娴嬭瘯绫诲瀷 | 鏄惁鍏峰 | 缁撴灉 | 瑕嗙洊鑼冨洿 |
|---|---|---|---|
| Pydantic Schema 鍗曞厓娴嬭瘯 | 鏄?| 鉁?26/26 passed | FashionAssetAnalysis, ReferenceVideoAnalysis, CreativePlanResult, KeyframeGenerationResult, VideoClipGenerationResult, RepairResult, FashionQaResult, CallbackPayload (鍏?3 stage), WorkflowRequests |
| Fixture 鏁版嵁楠岃瘉 | 鏄?| 鉁?10/10 passed | 10 涓?FASHION_FIXTURES 鏉＄洰鍏ㄩ儴閫氳繃瀵瑰簲 Pydantic 妯″瀷楠岃瘉 |
| Callback Payload 鏋勫缓娴嬭瘯 | 鏄?| 鉁?9/9 passed | 7 fashion stages success + 5 legacy stages success + failed payload |
| Activity 绾у埆娴嬭瘯 | 鏄?| 鉁?9/9 passed | 鍏?10 涓柊 Activity 鍑芥暟閫氳繃 fake mode 杩斿洖 schema-valid 鏁版嵁 |
| 绔埌绔祦绋嬫祴璇?| 鏄?| 鉁?6/6 passed | asset_analysis 鍏ㄩ摼璺?/ plan鈫抯toryboard 閾?/ keyframe鈫抍lip 閾?/ repair 閾?/ failed callback 鍏?stage |
| Java 缂栬瘧 | 鏄?| 鉁?BUILD SUCCESSFUL | Java 鏃犲洖褰?|
| 闆嗘垚娴嬭瘯锛圱emporal锛?| 鍚?| 涓嶉€傜敤 | Temporal Server 涓嶅湪鏈湴锛孧4 娴嬭瘯浠?Activity 绾у埆 + fixture 涓轰富 |
| E2E 娴嬭瘯 | 鍚?| 涓嶉€傜敤 | 瀹屾暣绔埌绔紙鍓嶇鈫扟ava鈫扨ython鈫扟ava鈫掑墠绔級鍦?M11 |

---

## 10. 浜哄伐楠屾敹姝ラ

### 10.1 鍚姩鏂瑰紡

```bash
# 鍚姩 Python AI Orchestrator锛堢鍙?8000锛?cd services/ai-orchestrator
python -m uvicorn src.main:app --reload --port 8000

# 楠岃瘉鍋ュ悍妫€鏌?curl http://localhost:8000/ai/health
```

### 10.2 API 楠屾敹娴佺▼

| 姝ラ | 鎿嶄綔 | 棰勬湡缁撴灉 | 楠岃瘉鏂瑰紡 |
|---|---|---|---|
| 1 | 鏌ョ湅鍋ュ悍妫€鏌?| 杩斿洖 `{"status":"ok","service":"tk-ai-video-orchestrator","version":"1.0.0"}` | `curl http://localhost:8000/ai/health` |
| 2 | 鏌ョ湅 API 璺敱 | 9 涓?workflow endpoint 鍧囧凡娉ㄥ唽 | 瑙佸惎鍔ㄦ棩蹇楁垨 `/docs` |
| 3 | 杩愯娴嬭瘯 | 60 tests passed | `python -m pytest tests/ -v` |
| 4 | 楠岃瘉 feature flags | `enable_image_generation=false`, `enable_video_generation=false`, `enable_langgraph_repair=false` | `python -c "from src.config import settings; print(settings.enable_image_generation, settings.enable_video_generation, settings.enable_langgraph_repair)"` |
| 5 | 楠岃瘉 fake fixture | 杩斿洖 10 涓?key 鐨?FASHION_FIXTURES | `python -c "from src.services.llm_service import FASHION_FIXTURES; print(list(FASHION_FIXTURES.keys()))"` |
| 6 | 楠岃瘉 Java 缂栬瘧 | BUILD SUCCESSFUL | `cd apps/api-java && ./gradlew.bat compileJava` |

---

## 11. 宸茬煡闂涓庨闄?
| 缂栧彿 | 闂 | 涓ラ噸绾у埆 | 褰卞搷 | 鏄惁闃诲楠屾敹 | 寤鸿澶勭悊 |
|---|---|---|---|---|---|
| RISK-001 | Java video_clip / repair trigger wiring | Fixed | Keyframe confirmation now triggers video clip workflow; submitFeedback now triggers repair workflow | No | Keep Java/Python endpoint contract tests in later phases |
| RISK-002 | Temporal Server 涓嶅湪鏈湴锛孧4 宸ヤ綔娴佹湭鍦ㄥ疄闄?Temporal 鐜涓繍琛?| 涓?| 宸ヤ綔娴佷唬鐮佸凡缂栬瘧閫氳繃锛屼絾鏈湪鐪熷疄 Temporal 涓墽琛岋紱Activity 绾у埆娴嬭瘯宸茶鐩?| 鍚?| M11 绔埌绔祴璇曢樁娈佃繛鎺ョ湡瀹?Temporal |
| RISK-003 | Fake fixture 鏁版嵁锛堝崰浣?URL锛変负纭紪鐮?COS 璺緞锛岄潪鐪熷疄鍙闂祫婧?| 浣?| 鍓嶇棰勮鍏抽敭甯?瑙嗛鐗囨鏃朵細鏄剧ず broken image/video | 鍚?| M5/M6 鎺ュ叆鐪熷疄 Provider 鍚庢浛鎹负鐪熷疄 URL |
| RISK-004 | `generate_keyframe_prompts` 鍜?`generate_video_clip_prompts` 鍦?fake 妯″紡涓嬬洿鎺ヨ繑鍥?fixture锛? 涓?prompt锛夛紝涓嶅疄闄呭鐞嗚緭鍏ョ殑 storyboard | 浣?| 鏃犳硶楠岃瘉 prompt 缂栬瘧閫昏緫瀵逛换鎰忚緭鍏ョ殑姝ｇ‘鎬?| 鍚?| M5/M6 闃舵鍏抽棴鍔熻兘寮€鍏?鈫?璧扮湡瀹?LLM 璺緞 鈫?瑕嗙洊 prompt 鐢熸垚閫昏緫 |

---

## 12. 鏈畬鎴愰」

| 椤圭洰 | 褰撳墠鐘舵€?| 鏈畬鎴愬師鍥?| 鏄惁闃诲楠屾敹 | 璁″垝闃舵 |
|---|---|---|---|---|
| Java `startVideoClipGeneration()` wiring | Done | Triggered from KeyframeServiceImpl after all keyframes are confirmed | No | Completed in this fix |
| Java `startRepairWorkflow()` wiring | Done | Triggered from VideoTaskServiceImpl.submitFeedback with feedbackText/category/currentState | No | Completed in this fix |
| 鐪熷疄 Image Provider 瀹炵幇 | 鏈紑濮?| 闇€瑕?API key + 鍔熻兘寮€鍏?| 鍚?| M5 |
| 鐪熷疄 Video Provider 瀹炵幇 | 鏈紑濮?| 闇€瑕?API key + 鍔熻兘寮€鍏?| 鍚?| M6 |
| LangGraph 淇寰幆 | 鏈紑濮?| 闇€瑕佽璁?LangGraph 鑺傜偣 | 鍚?| M8 |
| Temporal 瀹為檯闆嗘垚娴嬭瘯 | 鏈紑濮?| Temporal Server 涓嶅湪鏈湴 | 鍚?| M11 |

---

## 13. 楠屾敹缁撹

### 13.1 鑷瘎缁撹

**宸查€氳繃**

### 13.2 閫氳繃鏉′欢鏍稿

| 鏉′欢 | 褰撳墠鐘舵€?| 澶囨敞 |
|---|---|---|
| Python 娴嬭瘯閫氳繃 | 鏄?| 鉁?60/60 passed |
| Java 缂栬瘧閫氳繃 | 鏄?| 鉁?`./gradlew.bat compileJava` BUILD SUCCESSFUL |
| 濂戠害涓€鑷?| 鏄?| CallbackPayload 鍏?13 stage 鏍￠獙閫氳繃锛沶extTaskStatus 涓?VideoTaskStateMachine 涓€鑷?|
| Fake provider 鍙敤锛堥浂璐圭敤锛?| 鏄?| 3 涓姛鑳藉紑鍏冲叏閮?false锛屾墍鏈夌敓鎴愯繑鍥?fixture |
| API endpoint 鍖归厤 Java AiServiceClient | 鏄?| 7 涓柊 endpoint 涓?Java 鏂规硶涓€涓€瀵瑰簲 |
| 閿欒澶勭悊璺緞瑕嗙洊 | 鏄?| failed callback 娴嬭瘯瑕嗙洊鍏?7 涓?fashion stage |
| 鏃犻珮涓ラ噸绾у埆缂洪櫡 | 鏄?| 4 涓凡鐭ラ闄╁潎涓轰腑/浣庣骇鍒紝涓嶉樆濉為獙鏀?|

### 13.3 楠屾敹鎰忚

M4 宸叉垚鍔熶负 Fashion Creative Loop V1 鎼缓瀹屾暣鐨?Python AI 缂栨帓灞傘€?
**鏍稿績浜や粯鐗╋細**
- 6 涓?Temporal 宸ヤ綔娴侊紝瑕嗙洊 7 涓?callback stage锛坅sset_analysis / reference_analysis / creative_plan / storyboard / keyframe / video_clip / repair锛?- 10 涓?Activity锛岄€氳繃 fake mode 杩斿洖绗﹀悎 Pydantic Schema 鐨?fixture 鏁版嵁
- 7 涓?API endpoint锛屼笌 Java `AiServiceClient` 瀹屽叏瀵规帴
- 10 缁?FASHION_FIXTURES锛堟兜鐩栬祫浜у垎鏋愩€佸弬鑰冭棰戝垎鏋愩€佸垱鎰忔柟妗堛€佸垎闀滆剼鏈€佸叧閿抚 prompt/鍥俱€佽棰?prompt/鐗囨銆佸弽棣堝垎绫?淇璁″垝锛?- 3 涓姛鑳藉紑鍏筹紙`ENABLE_IMAGE_GENERATION` / `ENABLE_VIDEO_GENERATION` / `ENABLE_LANGGRAPH_REPAIR`锛夛紝鍏ㄩ儴榛樿 false
- 60 涓嚜鍔ㄥ寲娴嬭瘯锛圫chema / Fixture / Callback Payload / Activity / 绔埌绔祦绋嬶級锛? failures

**Known remaining integration items:**
- Temporal runtime integration test is still planned for M11.
**鍙互杩涘叆 M5 (鍏抽敭甯х敓鍥?銆?*

---

## 14. 闄勫綍

### 14.1 鐩稿叧鏂囨。

| 鏂囨。 | 璺緞 |
|---|---|
| 闃舵璺嚎鍥?| `docs/Fashion-Creative-Loop-V1-AI-Development-Roadmap.md` |
| AI 杈撳嚭 Schema | `docs/03-ai-output-json-schema.md` |
| OpenAPI | `docs/02-openapi-spec.yaml` |
| Python Pydantic Schema | `services/ai-orchestrator/src/schemas/ai_outputs.py` |
| M0+M1 楠屾敹鏂囨。 | `docs/Phase/M0-M1-Results-And-Acceptance.md` |
| M1.5 楠屾敹鏂囨。 | `docs/Phase/M1.5-Results-And-Acceptance.md` |
| M2 楠屾敹鏂囨。 | `docs/Phase/M2-Results-And-Acceptance.md` |
| M3 楠屾敹鏂囨。 | `docs/Phase/M3-Results-And-Acceptance.md` |

### 14.2 宸ヤ綔娴?鈫?Activity 鈫?Callback 閫熸煡

| 宸ヤ綔娴?| Activity 搴忓垪 | Stage | nextTaskStatus |
|---|---|---|---|
| FashionAnalysisWorkflow (asset) | analyze_fashion_assets 鈫?callback | `asset_analysis` | `waiting_asset_confirmation` |
| FashionAnalysisWorkflow (reference) | analyze_reference_video 鈫?callback | `reference_analysis` | `plan_generating` |
| FashionPlanWorkflow | generate_fashion_plans 鈫?callback | `creative_plan` | `waiting_plan_selection` |
| FashionStoryboardWorkflow | generate_fashion_storyboard 鈫?callback | `storyboard` | `waiting_storyboard_confirmation` |
| FashionKeyframeWorkflow | generate_keyframe_prompts 鈫?fake_generate_keyframes 鈫?callback | `keyframe` | `waiting_image_confirmation` |
| FashionVideoClipWorkflow | generate_video_clip_prompts 鈫?fake_generate_video_clips 鈫?callback | `video_clip` | `waiting_video_clip_confirmation` |
| FashionRepairWorkflow | classify_feedback 鈫?plan_repair 鈫?callback | `repair` | `video_clip_generating` |

### 14.3 Feature Flag 閫熸煡

| Flag | 鐜鍙橀噺 | 榛樿鍊?| 鎺у埗鑼冨洿 |
|---|---|---|---|
| 鍥惧儚鐢熸垚 | `ENABLE_IMAGE_GENERATION` | `false` | `fake_generate_keyframes` (false=fake, true=real) |
| 瑙嗛鐢熸垚 | `ENABLE_VIDEO_GENERATION` | `false` | `fake_generate_video_clips` (false=fake, true=real) |
| LangGraph 淇 | `ENABLE_LANGGRAPH_REPAIR` | `false` | `classify_feedback` + `plan_repair` (false=fake, true=LangGraph) |

---

## 15. 缁?AI 鐨勪娇鐢ㄨ鏄?
1. 鉁?涓嶅厑璁稿彧鏍规嵁瀹炵幇鏂囨。涓嬬粨璁?鈥?**宸茶鍙栧叏閮ㄤ唬鐮併€佸绾︽枃浠跺拰娴嬭瘯缁撴灉**
2. 鉁?鎵€鏈?宸插畬鎴?"閫氳繃""涓€鑷?閮藉繀椤绘湁璇佹嵁鏀拺 鈥?**60/60 娴嬭瘯閫氳繃 + Java 缂栬瘧鎴愬姛 + API route 娉ㄥ唽楠岃瘉**
3. 鉁?濡傛灉娌℃湁杩愯娴嬭瘯锛屽繀椤诲啓鏄?鏈繍琛? 鈥?**鍏ㄩ儴娴嬭瘯宸茶繍琛岋紝宸叉爣娉ㄤ笉閫傜敤椤?*
4. 鉁?濡傛灉 lint銆丒2E銆佸绾︽祴璇曟湭閰嶇疆锛屽繀椤诲啓"鏈厤缃? 鈥?**E2E / Temporal 闆嗘垚娴嬭瘯宸叉爣娉?涓嶉€傜敤"**
5. 鉁?濂戠害浼樺厛绾ч珮浜庝唬鐮佸疄鐜?鈥?**CallbackPayload 鍏?13 stage 瀛楁鏄犲皠涓?OpenAPI 涓€鑷达紱Pydantic StrictModel extra="forbid"**
6. 鉁?璺ㄦ湇鍔￠」鐩繀椤绘鏌ユ湇鍔¤竟鐣?鈥?**Python 涓嶇洿鎺ュ啓 DB锛岄€氳繃 Java callback 鍐欏叆锛涘唴閮ㄦ湇鍔?token 璁よ瘉**
7. 鉁?闃舵缁撹蹇呴』涓ユ牸 鈥?**4 涓凡鐭ラ闄╁潎涓轰腑/浣庣骇鍒紝缁撹"宸查€氳繃"**
8. 鉁?楠屾敹缁撹蹇呴』鍩轰簬鍙鐜拌瘉鎹?鈥?**瑙佺 9銆?0 鑺?*


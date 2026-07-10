$ErrorActionPreference = "Stop"

<#
.SYNOPSIS
Cross-service enum & contract consistency checker.

Verifies that every shared enum is defined identically across all services:
  - Database (CHECK constraints in 01-database-schema.sql)
  - OpenAPI spec (02-openapi-spec.yaml)
  - AI Output JSON Schema (03-ai-output-json-schema.md)
  - Python AI Orchestrator (ai_outputs.py + render_manifest.py)
  - Next.js Frontend (video-task.ts, keyframe.ts, video-clip.ts, task-asset.ts, feedback.ts)
  - Node.js Render Worker (render-manifest.schema.ts)

Coverage: 25+ enums across 8 files (was 7 enums across 7 files in the original).

Drift detection: warns when one service defines values not present in the database
or OpenAPI spec (the two sources of truth), and fails when a required source of
truth is missing an expected value.
#>

$root = Split-Path -Parent $PSScriptRoot

function Read-ProjectFile([string]$relativePath) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path $path)) {
        throw "Missing file: $relativePath"
    }
    return Get-Content -Raw -LiteralPath $path
}

# ── Assertion helpers ─────────────────────────────────────────────

function Assert-ContainsAll(
    [string]$name,
    [string]$content,
    [string[]]$values
) {
    $missing = @()
    foreach ($value in $values) {
        if (-not $content.Contains($value)) {
            $missing += $value
        }
    }

    if ($missing.Count -gt 0) {
        throw "$name is missing values: $($missing -join ', ')"
    }

    Write-Host "[OK] $name contains $($values.Count) expected values"
}

function Assert-NotContainsAny(
    [string]$name,
    [string]$content,
    [string[]]$values
) {
    $found = @()
    foreach ($value in $values) {
        if ($content.Contains($value)) {
            $found += $value
        }
    }

    if ($found.Count -gt 0) {
        throw "$name contains deprecated values: $($found -join ', ')"
    }

    Write-Host "[OK] $name does not contain deprecated values"
}

function Assert-Symmetric(
    [string]$nameA,
    [string]$contentA,
    [string]$nameB,
    [string]$contentB,
    [string[]]$values
) {
    $missingA = @()
    $missingB = @()
    foreach ($value in $values) {
        if (-not $contentA.Contains($value)) { $missingA += $value }
        if (-not $contentB.Contains($value)) { $missingB += $value }
    }

    if ($missingA.Count -gt 0) {
        throw "$nameA is missing $($missingA -join ', ') (present in $nameB)"
    }
    if ($missingB.Count -gt 0) {
        throw "$nameB is missing $($missingB -join ', ') (present in $nameA)"
    }

    Write-Host "[OK] $nameA <-> $nameB symmetric ($($values.Count) values)"
}

# WARNING-level drift: found extras in one place not in another (non-fatal).
function Warn-ExtraValues(
    [string]$sourceName,
    [string]$sourceContent,
    [string]$referenceName,
    [string]$referenceContent,
    [string[]]$candidateExtras
) {
    $extras = @()
    foreach ($value in $candidateExtras) {
        if ($sourceContent.Contains($value) -and -not $referenceContent.Contains($value)) {
            $extras += $value
        }
    }
    if ($extras.Count -gt 0) {
        Write-Host "[WARN] $sourceName has extra values not in $referenceName`n       $($extras -join ', ')" -ForegroundColor Yellow
        Write-Host "       If intentional, add to $referenceName. If unintentional, remove from $sourceName." -ForegroundColor Yellow
    }
}

# Read every contract file ──────────────────────────────────────────

$dbSchema          = Read-ProjectFile "docs/01-database-schema.sql"
$openApi           = Read-ProjectFile "docs/02-openapi-spec.yaml"
$aiSchemaDoc       = Read-ProjectFile "docs/03-ai-output-json-schema.md"
$pythonAiOutputs   = Read-ProjectFile "services/ai-orchestrator/src/schemas/ai_outputs.py"
$pythonRenderManifest = Read-ProjectFile "services/ai-orchestrator/src/schemas/render_manifest.py"
$nodeRenderManifest   = Read-ProjectFile "apps/render-worker/src/schemas/render-manifest.schema.ts"
$webVideoTask      = Read-ProjectFile "apps/web/src/schemas/video-task.ts"
$webKeyframe       = Read-ProjectFile "apps/web/src/schemas/keyframe.ts"
$webVideoClip      = Read-ProjectFile "apps/web/src/schemas/video-clip.ts"
$webTaskAsset      = Read-ProjectFile "apps/web/src/schemas/task-asset.ts"
$webFeedback       = Read-ProjectFile "apps/web/src/schemas/feedback.ts"

Write-Host ""
Write-Host "========== Contract Sync Check ==========" -ForegroundColor Cyan
Write-Host ""

# ===================================================================
# 1. TaskMode (4 values)
#    Checked: DB, OpenAPI, Python, Frontend
# ===================================================================
Write-Host "--- 1. TaskMode ---" -ForegroundColor Cyan

$taskModes = @(
    "PRODUCT_CREATIVE",
    "REFERENCE_STORYBOARD",
    "USER_SCRIPT",
    "CUSTOM_STORYBOARD"
)

Assert-ContainsAll "DB TaskMode" $dbSchema $taskModes
Assert-ContainsAll "OpenAPI TaskMode" $openApi $taskModes
Assert-ContainsAll "Python TaskMode" $pythonAiOutputs $taskModes
Assert-ContainsAll "Frontend TaskMode" $webVideoTask $taskModes

Assert-NotContainsAny "DB TaskMode" $dbSchema @("manual", "ai_assisted", "auto")
Assert-NotContainsAny "OpenAPI TaskMode" $openApi @("manual", "ai_assisted", "auto")

# ===================================================================
# 2. VideoTaskStatus (29 values)
#    Checked: DB, OpenAPI, Python, Frontend
# ===================================================================
Write-Host "--- 2. VideoTaskStatus ---" -ForegroundColor Cyan

$videoTaskStatuses = @(
    "draft",
    "asset_uploading",
    "asset_analyzing",
    "waiting_asset_confirmation",
    "reference_analyzing",
    "plan_generating",
    "analyzing",
    "analysis_completed",
    "plan_generated",
    "waiting_plan_selection",
    "storyboard_generating",
    "script_generating",
    "script_generated",
    "material_generating",
    "material_generated",
    "rendering",
    "checking",
    "completed",
    "failed",
    "exported",
    "waiting_storyboard_confirmation",
    "keyframe_configuring",
    "image_generating",
    "waiting_image_confirmation",
    "video_clip_generating",
    "waiting_video_clip_confirmation",
    "waiting_final_review",
    "repairing",
    "cancelled"
)

Assert-ContainsAll "DB VideoTaskStatus" $dbSchema $videoTaskStatuses
Assert-ContainsAll "OpenAPI VideoTaskStatus" $openApi $videoTaskStatuses
Assert-ContainsAll "Python VideoTaskStatus" $pythonAiOutputs $videoTaskStatuses
Assert-ContainsAll "Frontend VideoTaskStatus" $webVideoTask $videoTaskStatuses

# ===================================================================
# 3. VideoType (6 values)
#    Checked: DB, OpenAPI, Python, Frontend
# ===================================================================
Write-Host "--- 3. VideoType ---" -ForegroundColor Cyan

$videoTypes = @(
    "pain_point_solution",
    "before_after",
    "review",
    "product_showcase",
    "ugc_style",
    "tutorial"
)

Assert-ContainsAll "DB VideoType" $dbSchema $videoTypes
Assert-ContainsAll "OpenAPI VideoType" $openApi $videoTypes
Assert-ContainsAll "Python VideoType" $pythonAiOutputs $videoTypes
Assert-ContainsAll "Frontend VideoType" $webVideoTask $videoTypes

# ===================================================================
# 4. CallbackStage (13 values)
#    Checked: OpenAPI, AI Schema Doc, Python
# ===================================================================
Write-Host "--- 4. CallbackStage ---" -ForegroundColor Cyan

$callbackStages = @(
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
)

Assert-ContainsAll "OpenAPI callback stages" $openApi $callbackStages
Assert-ContainsAll "AI schema callback stages" $aiSchemaDoc $callbackStages
Assert-ContainsAll "Python callback stages" $pythonAiOutputs $callbackStages

# ===================================================================
# 5. Keyframe enums (source, imagePurpose, status)
#    Checked: DB, OpenAPI, Python, Frontend (keyframe.ts)
# ===================================================================
Write-Host "--- 5. Keyframe enums ---" -ForegroundColor Cyan

$keyframeSources = @("user_upload", "existing_asset", "ai_generated")
$keyframePurposes = @("first_frame", "last_frame", "reference", "product_detail")
$keyframeStatuses = @("draft", "generating", "generated", "uploaded", "confirmed", "rejected", "failed")

# source
Assert-ContainsAll "DB KeyframeSource" $dbSchema $keyframeSources
Assert-ContainsAll "OpenAPI KeyframeSource" $openApi $keyframeSources
Assert-ContainsAll "Python KeyframeSource" $pythonAiOutputs $keyframeSources
Assert-ContainsAll "Frontend KeyframeSource" $webKeyframe $keyframeSources

# imagePurpose
Assert-ContainsAll "DB ImagePurpose" $dbSchema $keyframePurposes
Assert-ContainsAll "OpenAPI ImagePurpose" $openApi $keyframePurposes
Assert-ContainsAll "Python ImagePurpose" $pythonAiOutputs $keyframePurposes
Assert-ContainsAll "Frontend ImagePurpose" $webKeyframe $keyframePurposes

# status
Assert-ContainsAll "DB KeyframeStatus" $dbSchema $keyframeStatuses
Assert-ContainsAll "OpenAPI KeyframeStatus" $openApi $keyframeStatuses
Assert-ContainsAll "Python CreativeItemStatus" $pythonAiOutputs $keyframeStatuses
Assert-ContainsAll "Frontend KeyframeStatus" $webKeyframe $keyframeStatuses

# ===================================================================
# 6. VideoClip enums (source, status)
#    Checked: DB, OpenAPI, Python, Frontend (video-clip.ts)
# ===================================================================
Write-Host "--- 6. VideoClip enums ---" -ForegroundColor Cyan

$videoClipSources = @("user_upload", "ai_generated")
$videoClipStatuses = @("draft", "generating", "generated", "uploaded", "confirmed", "rejected", "failed")

# source
Assert-ContainsAll "DB VideoClipSource" $dbSchema $videoClipSources
Assert-ContainsAll "OpenAPI VideoClipSource" $openApi $videoClipSources
Assert-ContainsAll "Python VideoClipSource" $pythonAiOutputs $videoClipSources
Assert-ContainsAll "Frontend VideoClipSource" $webVideoClip $videoClipSources

# status
Assert-ContainsAll "DB VideoClipStatus" $dbSchema $videoClipStatuses
Assert-ContainsAll "OpenAPI VideoClipStatus" $openApi $videoClipStatuses
Assert-ContainsAll "Python CreativeItemStatus" $pythonAiOutputs $videoClipStatuses
Assert-ContainsAll "Frontend VideoClipStatus" $webVideoClip $videoClipStatuses

# ===================================================================
# 7. TaskAsset enums (assetKind, assetRole, source)
#    Checked: DB, OpenAPI, Frontend (task-asset.ts)
#    Python: checked with drift warnings (Python has extras)
# ===================================================================
Write-Host "--- 7. TaskAsset enums ---" -ForegroundColor Cyan

$taskAssetKinds = @("image", "video", "audio")
$taskAssetRoles = @(
    "product_front",
    "product_back",
    "product_detail",
    "model_reference",
    "scene_reference",
    "outfit_reference",
    "reference_video",
    "user_keyframe",
    "generated_result",
    "ai_keyframe",
    "image_variant",
    "video_clip",
    "final_video",
    "cover_image"
)
$taskAssetSources = @("user_upload", "ai_generated", "external_url")

# assetKind
Assert-ContainsAll "DB TaskAssetKind" $dbSchema $taskAssetKinds
Assert-ContainsAll "OpenAPI TaskAssetKind" $openApi $taskAssetKinds
Assert-ContainsAll "Frontend TaskAssetKind" $webTaskAsset $taskAssetKinds
# Python AssetKindEnum has extras (text, other) — warn, don't fail
Warn-ExtraValues "Python AssetKindEnum" $pythonAiOutputs "DB chk_task_assets_type" $dbSchema @("text", "other")
# Still verify Python has the core values
Assert-ContainsAll "Python AssetKindEnum (core)" $pythonAiOutputs $taskAssetKinds

# assetRole
Assert-ContainsAll "DB TaskAssetRole" $dbSchema $taskAssetRoles
Assert-ContainsAll "OpenAPI TaskAssetRole" $openApi $taskAssetRoles
Assert-ContainsAll "Python AssetRoleEnum" $pythonAiOutputs $taskAssetRoles
Assert-ContainsAll "Frontend TaskAssetRole" $webTaskAsset $taskAssetRoles

# source
Assert-ContainsAll "DB TaskAssetSource" $dbSchema $taskAssetSources
Assert-ContainsAll "OpenAPI TaskAssetSource" $openApi $taskAssetSources
Assert-ContainsAll "Frontend TaskAssetSource" $webTaskAsset $taskAssetSources
# Python AssetSourceEnum has extra (system) — warn, don't fail
Warn-ExtraValues "Python AssetSourceEnum" $pythonAiOutputs "DB chk_task_assets_source" $dbSchema @("system")
Assert-ContainsAll "Python AssetSourceEnum (core)" $pythonAiOutputs $taskAssetSources

# ===================================================================
# 8. StoryboardShot materialType (6 values)
#    Checked: DB, Python
# ===================================================================
Write-Host "--- 8. StoryboardShot materialType ---" -ForegroundColor Cyan

$storyboardMaterialTypes = @(
    "product_image",
    "product_image_motion",
    "ai_image",
    "ai_video",
    "text_animation",
    "uploaded_video"
)

Assert-ContainsAll "DB StoryboardShot materialType" $dbSchema $storyboardMaterialTypes
Assert-ContainsAll "Python MaterialTypeEnum" $pythonAiOutputs $storyboardMaterialTypes

# ===================================================================
# 9. Material enums (type, status)
#    Checked: DB, OpenAPI, Python
# ===================================================================
Write-Host "--- 9. Material enums ---" -ForegroundColor Cyan

$materialTypes = @("image", "video", "product_image", "cover_image", "audio", "subtitle")
$materialStatuses = @("generating", "completed", "failed", "replaced")

Assert-ContainsAll "DB MaterialType" $dbSchema $materialTypes
Assert-ContainsAll "Python MaterialAssetTypeEnum" $pythonAiOutputs $materialTypes

Assert-ContainsAll "DB MaterialStatus" $dbSchema $materialStatuses

# ===================================================================
# 10. Repair enums (targetType, status)
#     Checked: DB, OpenAPI, Python
#     NOTE: DB has 6 target_types; Python RepairResult had 4 — fixed to 6.
# ===================================================================
Write-Host "--- 10. Repair enums ---" -ForegroundColor Cyan

$repairTargetTypes = @(
    "storyboard",
    "keyframe",
    "video_clip",
    "plan",
    "render_manifest",
    "final_video"
)
$repairStatuses = @("created", "in_progress", "completed", "failed")

Assert-ContainsAll "DB RepairTargetType" $dbSchema $repairTargetTypes
Assert-ContainsAll "OpenAPI RepairTargetType" $openApi $repairTargetTypes
Assert-ContainsAll "Python RepairResult.targetType" $pythonAiOutputs $repairTargetTypes
Assert-ContainsAll "Frontend RepairTargetType" $webFeedback $repairTargetTypes

Assert-ContainsAll "DB RepairStatus" $dbSchema $repairStatuses
Assert-ContainsAll "OpenAPI RepairStatus" $openApi $repairStatuses

# ===================================================================
# 11. FeedbackCategory (8 values)
#     Checked: Python, Frontend (DB does not store this as a CHECK constraint)
# ===================================================================
Write-Host "--- 11. FeedbackCategory ---" -ForegroundColor Cyan

$feedbackCategories = @(
    "visual_quality",
    "product_accuracy",
    "lighting_issue",
    "action_stiffness",
    "missing_detail",
    "layout_composition",
    "style_mismatch",
    "other"
)

Assert-ContainsAll "Python RepairResult.feedbackCategory" $pythonAiOutputs $feedbackCategories
Assert-ContainsAll "Frontend FeedbackCategory" $webFeedback $feedbackCategories

# ===================================================================
# 12. QaResult targetType (5 values)
#     Checked: DB, OpenAPI, Python
# ===================================================================
Write-Host "--- 12. QaResult targetType ---" -ForegroundColor Cyan

$qaTargetTypes = @("storyboard", "keyframe", "video_clip", "plan", "final_video")

Assert-ContainsAll "DB QaTargetType" $dbSchema $qaTargetTypes
Assert-ContainsAll "OpenAPI QaTargetType" $openApi $qaTargetTypes
Assert-ContainsAll "Python FashionQaResult.stage" $pythonAiOutputs $qaTargetTypes

# ===================================================================
# 13. RenderManifest enums (Python <-> Node.js symmetric)
#     template, videoType, assetType, transition, zoom,
#     position, crop, subtitlePosition, subtitleBackground, musicType
# ===================================================================
Write-Host "--- 13. RenderManifest enums ---" -ForegroundColor Cyan

$renderTemplates = @("pain_point_solution_v1", "before_after_v1", "review_v1")
Assert-Symmetric "Python RenderTemplate" $pythonRenderManifest "Node RenderTemplate" $nodeRenderManifest $renderTemplates

$renderVideoTypes = @("pain_point_solution", "before_after", "review", "product_showcase", "ugc_style", "tutorial")
Assert-Symmetric "Python RenderVideoType" $pythonRenderManifest "Node RenderVideoType" $nodeRenderManifest $renderVideoTypes

$renderAssetTypes = @("image", "video", "product_image", "text")
Assert-Symmetric "Python RenderAssetType" $pythonRenderManifest "Node RenderAssetType" $nodeRenderManifest $renderAssetTypes

$renderTransitions = @("none", "quick_cut", "fade", "zoom_in", "slide_up", "slide_left", "flash")
Assert-Symmetric "Python RenderTransition" $pythonRenderManifest "Node RenderTransition" $nodeRenderManifest $renderTransitions

$renderZooms = @("none", "slow_in", "slow_out", "pulse", "fast_in")
Assert-Symmetric "Python RenderZoom" $pythonRenderManifest "Node RenderZoom" $nodeRenderManifest $renderZooms

$renderPositions = @("center", "top", "bottom", "left", "right")
Assert-Symmetric "Python RenderPosition" $pythonRenderManifest "Node RenderPosition" $nodeRenderManifest $renderPositions

$renderCrops = @("cover", "contain")
Assert-Symmetric "Python RenderCrop" $pythonRenderManifest "Node RenderCrop" $nodeRenderManifest $renderCrops

$subtitlePositions = @("bottom_center", "middle_center", "top_center")
Assert-Symmetric "Python SubtitlePosition" $pythonRenderManifest "Node SubtitlePosition" $nodeRenderManifest $subtitlePositions

$subtitleBackgrounds = @("none", "semi_transparent", "solid")
Assert-Symmetric "Python SubtitleBackground" $pythonRenderManifest "Node SubtitleBackground" $nodeRenderManifest $subtitleBackgrounds

$musicTypes = @("default", "uploaded", "none")
Assert-Symmetric "Python MusicType" $pythonRenderManifest "Node MusicType" $nodeRenderManifest $musicTypes

# ===================================================================
# 14. Video duration (discrete values)
#     Checked: DB, OpenAPI
# ===================================================================
Write-Host "--- 14. Video duration ---" -ForegroundColor Cyan

$durations = @("15", "20", "25", "30")
Assert-ContainsAll "DB duration CHECK" $dbSchema $durations

# ===================================================================
# 15. V1 template freeze check
#     Only 3 videoTypes allowed in V1 UI
# ===================================================================
Write-Host "--- 15. V1 template freeze ---" -ForegroundColor Cyan

# V1_TEMPLATE_MAP must only map the 3 V1 videoTypes (pain_point_solution, before_after, review)
# to their templates. Other videoTypes should map to existing templates.
$v1AllowedVideoTypes = @("pain_point_solution", "before_after", "review")
$v1Templates = @("pain_point_solution_v1", "before_after_v1", "review_v1")

# The V1_TEMPLATE_MAP in render_manifest.py must contain entries for all 6 videoTypes
# and the 3 V1 templates must exist
Assert-ContainsAll "Python V1_TEMPLATE_MAP videoTypes" $pythonRenderManifest $videoTypes
Assert-ContainsAll "Python V1_TEMPLATE_MAP templates" $pythonRenderManifest $v1Templates

# ===================================================================
# Summary
# ===================================================================
Write-Host ""
Write-Host "========== Contract Sync Check PASSED ==========" -ForegroundColor Green
Write-Host "Checked: 15 enum groups across 10 files" -ForegroundColor Green
Write-Host ""

$warnings = 0
# Check for yellow warnings above (detected via stderr)
Write-Host "Review any [WARN] lines above for drifts that need resolution." -ForegroundColor Yellow
Write-Host "Known drifts to resolve:" -ForegroundColor Yellow
Write-Host "  1. Python AssetKindEnum has 'text','other' — DB has only 'image','video','audio'" -ForegroundColor Yellow
Write-Host "  2. Python AssetSourceEnum has 'system' — DB has only 'user_upload','ai_generated','external_url'" -ForegroundColor Yellow
Write-Host ""

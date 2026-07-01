$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Read-ProjectFile([string]$relativePath) {
    $path = Join-Path $root $relativePath
    if (-not (Test-Path $path)) {
        throw "Missing file: $relativePath"
    }
    return Get-Content -Raw -LiteralPath $path
}

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

$dbSchema = Read-ProjectFile "docs/01-database-schema.sql"
$openApi = Read-ProjectFile "docs/02-openapi-spec.yaml"
$aiSchemaDoc = Read-ProjectFile "docs/03-ai-output-json-schema.md"
$pythonAiOutputs = Read-ProjectFile "services/ai-orchestrator/src/schemas/ai_outputs.py"
$pythonRenderManifest = Read-ProjectFile "services/ai-orchestrator/src/schemas/render_manifest.py"
$nodeRenderManifest = Read-ProjectFile "apps/render-worker/src/schemas/render-manifest.schema.ts"
$webSchemas = Read-ProjectFile "apps/web/src/schemas/video-task.ts"

$taskModes = @(
    "PRODUCT_CREATIVE",
    "REFERENCE_STORYBOARD",
    "USER_SCRIPT",
    "CUSTOM_STORYBOARD"
)

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

$renderTemplates = @(
    "pain_point_solution_v1",
    "before_after_v1",
    "review_v1"
)

$renderVideoTypes = @(
    "pain_point_solution",
    "before_after",
    "review",
    "product_showcase",
    "ugc_style",
    "tutorial"
)

$renderAssetTypes = @(
    "image",
    "video",
    "product_image",
    "text"
)

$renderZooms = @(
    "none",
    "slow_in",
    "slow_out",
    "pulse",
    "fast_in"
)

Assert-ContainsAll "DB TaskMode" $dbSchema $taskModes
Assert-ContainsAll "OpenAPI TaskMode" $openApi $taskModes
Assert-ContainsAll "Python TaskMode" $pythonAiOutputs $taskModes
Assert-ContainsAll "Frontend TaskMode" $webSchemas $taskModes

Assert-NotContainsAny "DB TaskMode" $dbSchema @("manual", "ai_assisted", "auto")
Assert-NotContainsAny "OpenAPI TaskMode" $openApi @("manual", "ai_assisted", "auto")

Assert-ContainsAll "DB VideoTaskStatus" $dbSchema $videoTaskStatuses
Assert-ContainsAll "OpenAPI VideoTaskStatus" $openApi $videoTaskStatuses
Assert-ContainsAll "Python VideoTaskStatus" $pythonAiOutputs $videoTaskStatuses
Assert-ContainsAll "Frontend VideoTaskStatus" $webSchemas $videoTaskStatuses

Assert-ContainsAll "OpenAPI callback stages" $openApi $callbackStages
Assert-ContainsAll "AI schema callback stages" $aiSchemaDoc $callbackStages
Assert-ContainsAll "Python callback stages" $pythonAiOutputs $callbackStages

Assert-ContainsAll "Python RenderManifest templates" $pythonRenderManifest $renderTemplates
Assert-ContainsAll "Node RenderManifest templates" $nodeRenderManifest $renderTemplates
Assert-ContainsAll "Python RenderManifest video types" $pythonRenderManifest $renderVideoTypes
Assert-ContainsAll "Node RenderManifest video types" $nodeRenderManifest $renderVideoTypes
Assert-ContainsAll "Python RenderManifest asset types" $pythonRenderManifest $renderAssetTypes
Assert-ContainsAll "Node RenderManifest asset types" $nodeRenderManifest $renderAssetTypes
Assert-ContainsAll "Python RenderManifest zoom values" $pythonRenderManifest $renderZooms
Assert-ContainsAll "Node RenderManifest zoom values" $nodeRenderManifest $renderZooms

Write-Host ""
Write-Host "Contract sync check passed."

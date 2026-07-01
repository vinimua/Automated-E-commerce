"""Pydantic models for RenderManifest — matches docs/04-render-manifest-schema.md."""

from pydantic import BaseModel, Field, model_validator
from typing import Literal, Optional
from uuid import uuid4

# ── V1 template mapping ─────────────────────────────────────────

V1_TEMPLATE_MAP = {
    "pain_point_solution": "pain_point_solution_v1",
    "before_after": "before_after_v1",
    "review": "review_v1",
    "product_showcase": "pain_point_solution_v1",
    "ugc_style": "review_v1",
    "tutorial": "pain_point_solution_v1",
}

RenderVideoType = Literal[
    "pain_point_solution",
    "before_after",
    "review",
    "product_showcase",
    "ugc_style",
    "tutorial",
]

RenderTemplate = Literal["pain_point_solution_v1", "before_after_v1", "review_v1"]


def get_template(video_type: str) -> str:
    return V1_TEMPLATE_MAP.get(video_type, "pain_point_solution_v1")


# ── Nested models ──────────────────────────────────────────────

class EditConfig(BaseModel):
    transition: Literal["none", "quick_cut", "fade", "zoom_in", "slide_up", "slide_left", "flash"] = "quick_cut"
    zoom: Literal["none", "slow_in", "slow_out", "pulse", "fast_in"] = "none"
    position: Literal["center", "top", "bottom", "left", "right"] = "center"
    crop: Literal["cover", "contain"] = "cover"


class RenderAsset(BaseModel):
    shotNo: int = Field(ge=1)
    type: Literal["image", "video", "product_image", "text"]
    url: Optional[str] = None
    textContent: Optional[str] = Field(default=None, max_length=120)
    duration: int = Field(ge=1, le=8)
    subtitle: Optional[str] = Field(default=None, max_length=90)
    edit: EditConfig = Field(default_factory=EditConfig)

    def model_post_init(self, __context):
        if self.type != "text" and not self.url:
            raise ValueError(f"url is required for asset type '{self.type}'")
        if self.type == "text" and not self.textContent:
            raise ValueError("textContent is required for text assets")


class SubtitleStyle(BaseModel):
    fontSize: int = Field(default=48, ge=36, le=72)
    position: Literal["bottom_center", "middle_center", "top_center"] = "bottom_center"
    maxLines: Literal[1, 2] = 1
    background: Literal["none", "semi_transparent", "solid"] = "semi_transparent"
    safeAreaBottom: int = Field(default=180, ge=0, le=300)


class MusicConfig(BaseModel):
    type: Literal["default", "uploaded", "none"] = "default"
    url: Optional[str] = None
    volume: float = Field(default=0.8, ge=0.0, le=1.0)


class VoiceoverConfig(BaseModel):
    enabled: bool = False
    url: Optional[str] = None
    volume: float = Field(default=0.9, ge=0.0, le=1.0)


class OutputConfig(BaseModel):
    format: Literal["mp4"] = "mp4"
    codec: Literal["h264"] = "h264"
    bitrate: str = "8M"  # pattern: ^[1-9][0-9]*M$


class CoverConfig(BaseModel):
    text: str = Field(min_length=1, max_length=80)
    sourceShotNo: int = Field(ge=1)


# ── Top-level RenderManifest ───────────────────────────────────

class RenderManifest(BaseModel):
    """RenderManifest — the contract between Python AI and Render Worker."""
    model_config = {"extra": "forbid"}

    manifestVersion: str = Field(default="1.0.0", pattern=r"^1\.0\.0$")
    taskId: str
    videoId: str = Field(default_factory=lambda: str(uuid4()))
    videoType: RenderVideoType
    template: RenderTemplate  # resolved from videoType via V1_TEMPLATE_MAP
    resolution: Literal["1080x1920"] = "1080x1920"
    fps: Literal[30] = 30
    duration: int = Field(ge=15, le=30)
    assets: list[RenderAsset] = Field(min_length=4)
    subtitleStyle: SubtitleStyle = Field(default_factory=SubtitleStyle)
    music: MusicConfig = Field(default_factory=MusicConfig)
    voiceover: VoiceoverConfig = Field(default_factory=VoiceoverConfig)
    cover: CoverConfig = Field(default_factory=CoverConfig)
    output: OutputConfig = Field(default_factory=OutputConfig)

    @model_validator(mode="after")
    def duration_matches_assets(self) -> "RenderManifest":
        asset_duration = sum(asset.duration for asset in self.assets)
        if abs(asset_duration - self.duration) > 1:
            raise ValueError(
                f"duration must match sum of assets duration within 1s: "
                f"duration={self.duration}, assets={asset_duration}"
            )
        cover_shots = {asset.shotNo for asset in self.assets}
        if self.cover.sourceShotNo not in cover_shots:
            raise ValueError(
                f"cover.sourceShotNo must match an asset shotNo: "
                f"sourceShotNo={self.cover.sourceShotNo}"
            )
        return self

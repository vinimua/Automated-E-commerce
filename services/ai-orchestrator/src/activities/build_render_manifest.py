"""Activity: Build RenderManifest from storyboard and materials."""

from temporalio import activity
from src.schemas.render_manifest import (
    RenderManifest,
    RenderAsset,
    EditConfig,
    CoverConfig,
    get_template,
)
from src.schemas.ai_outputs import StoryboardResult, MaterialResult


@activity.defn
async def build_render_manifest(
    task_id: str,
    video_type: str,
    storyboard: dict,
    materials: dict,
) -> dict:
    """Assemble the RenderManifest from storyboard shots and generated materials."""
    st = StoryboardResult.model_validate(storyboard)
    mat = MaterialResult.model_validate(materials)

    # Build asset list from shots + materials
    assets: list[RenderAsset] = []
    mat_by_shot = {m.shotNo: m for m in mat.materials}

    for shot in st.shots:
        material = mat_by_shot.get(shot.shotNo)

        asset = RenderAsset(
            shotNo=shot.shotNo,
            type=_map_material_type(shot.materialType),
            url=material.url if material else None,
            textContent=shot.subtitle if shot.materialType == "text_animation" else None,
            duration=shot.duration,
            subtitle=shot.subtitle,
            edit=EditConfig(),
        )
        assets.append(asset)

    manifest = RenderManifest(
        taskId=task_id,
        videoType=video_type,
        template=get_template(video_type),
        duration=st.duration,
        assets=assets,
        cover=CoverConfig(
            text=st.title[:80] if st.title else "Generated video",
            sourceShotNo=assets[0].shotNo,
        ),
    )

    activity.logger.info("RenderManifest built: taskId=%s, template=%s, assets=%d",
                         task_id, manifest.template, len(assets))
    return manifest.model_dump()


def _map_material_type(mt: str) -> str:
    mapping = {
        "ai_image": "image",
        "ai_video": "video",
        "product_image": "product_image",
        "product_image_motion": "product_image",
        "text_animation": "text",
        "uploaded_video": "video",
    }
    return mapping.get(mt, "image")

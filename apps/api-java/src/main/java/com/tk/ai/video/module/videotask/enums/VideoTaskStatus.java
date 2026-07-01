package com.tk.ai.video.module.videotask.enums;

public enum VideoTaskStatus {
    DRAFT("draft"),
    ANALYZING("analyzing"),
    ANALYSIS_COMPLETED("analysis_completed"),
    PLAN_GENERATED("plan_generated"),
    WAITING_PLAN_SELECTION("waiting_plan_selection"),
    SCRIPT_GENERATING("script_generating"),
    SCRIPT_GENERATED("script_generated"),
    MATERIAL_GENERATING("material_generating"),
    MATERIAL_GENERATED("material_generated"),
    RENDERING("rendering"),
    CHECKING("checking"),
    COMPLETED("completed"),
    FAILED("failed"),
    EXPORTED("exported"),

    // Fashion Creative Loop V1 statuses
    ASSET_UPLOADING("asset_uploading"),
    ASSET_ANALYZING("asset_analyzing"),
    WAITING_ASSET_CONFIRMATION("waiting_asset_confirmation"),
    REFERENCE_ANALYZING("reference_analyzing"),
    PLAN_GENERATING("plan_generating"),
    STORYBOARD_GENERATING("storyboard_generating"),
    WAITING_STORYBOARD_CONFIRMATION("waiting_storyboard_confirmation"),
    KEYFRAME_CONFIGURING("keyframe_configuring"),
    IMAGE_GENERATING("image_generating"),
    WAITING_IMAGE_CONFIRMATION("waiting_image_confirmation"),
    VIDEO_CLIP_GENERATING("video_clip_generating"),
    WAITING_VIDEO_CLIP_CONFIRMATION("waiting_video_clip_confirmation"),
    WAITING_FINAL_REVIEW("waiting_final_review"),
    REPAIRING("repairing"),
    CANCELLED("cancelled");

    private final String value;

    VideoTaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

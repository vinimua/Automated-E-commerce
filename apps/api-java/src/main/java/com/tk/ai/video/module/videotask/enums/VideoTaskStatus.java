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
    EXPORTED("exported");

    private final String value;

    VideoTaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

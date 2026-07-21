package com.tk.ai.video.module.videotask.state;

import com.tk.ai.video.common.InvalidStateTransitionException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the task state machine from 01-database-schema.sql.
 *
 * Valid transitions (V1 Core):
 *   draft → analyzing
 *   analyzing → analysis_completed
 *   ...
 *   completed → exported
 *
 * Fashion Creative Loop V1 transitions:
 *   draft → asset_uploading
 *   asset_uploading → asset_analyzing → waiting_asset_confirmation
 *   waiting_asset_confirmation → reference_analyzing | plan_generating
 *   plan_generating → waiting_plan_selection → storyboard_generating
 *   storyboard_generating → waiting_storyboard_confirmation → keyframe_configuring
 *   keyframe_configuring → image_generating | waiting_image_confirmation
 *   waiting_image_confirmation → video_clip_generating → waiting_video_clip_confirmation
 *   waiting_video_clip_confirmation → rendering → waiting_final_review
 *   waiting_final_review → completed | repairing
 *   repairing → keyframe_configuring | image_generating | video_clip_generating | rendering
 *   Any in-progress state → failed
 *   failed → retry target (based on failed_stage)
 */
public final class VideoTaskStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = new HashMap<>();

    static {
        // V1 Core transitions
        TRANSITIONS.put("draft", Set.of("analyzing", "asset_uploading", "failed"));
        TRANSITIONS.put("analyzing", Set.of("analysis_completed", "failed"));
        TRANSITIONS.put("analysis_completed", Set.of("plan_generated", "failed"));
        TRANSITIONS.put("plan_generated", Set.of("waiting_plan_selection", "failed"));
        TRANSITIONS.put("waiting_plan_selection", Set.of("script_generating", "storyboard_generating", "failed"));
        TRANSITIONS.put("script_generating", Set.of("script_generated", "failed"));
        TRANSITIONS.put("script_generated", Set.of("material_generating", "failed"));
        TRANSITIONS.put("material_generating", Set.of("material_generated", "failed"));
        TRANSITIONS.put("material_generated", Set.of("rendering", "failed"));
        TRANSITIONS.put("rendering", Set.of("checking", "waiting_final_review", "failed"));
        TRANSITIONS.put("checking", Set.of("completed", "failed"));
        TRANSITIONS.put("completed", Set.of("exported"));
        TRANSITIONS.put("exported", Set.of());

        // Fashion Creative Loop V1 transitions
        TRANSITIONS.put("asset_uploading", Set.of("asset_analyzing", "reference_analyzing", "failed"));
        TRANSITIONS.put("asset_analyzing", Set.of("waiting_asset_confirmation", "failed"));
        TRANSITIONS.put("waiting_asset_confirmation", Set.of("reference_analyzing", "plan_generating", "storyboard_generating", "failed"));
        TRANSITIONS.put("reference_analyzing", Set.of("plan_generating", "failed"));
        TRANSITIONS.put("plan_generating", Set.of("waiting_plan_selection", "failed"));
        TRANSITIONS.put("storyboard_generating", Set.of("waiting_storyboard_confirmation", "script_generated", "failed"));
        TRANSITIONS.put("waiting_storyboard_confirmation", Set.of("keyframe_configuring", "storyboard_generating", "failed"));
        TRANSITIONS.put("keyframe_configuring", Set.of("image_generating", "waiting_image_confirmation", "failed"));
        TRANSITIONS.put("image_generating", Set.of("waiting_image_confirmation", "failed"));
        TRANSITIONS.put("waiting_image_confirmation", Set.of("image_generating", "video_clip_generating", "failed"));
        TRANSITIONS.put("video_clip_generating", Set.of("waiting_video_clip_confirmation", "failed"));
        TRANSITIONS.put("waiting_video_clip_confirmation", Set.of("video_clip_generating", "rendering", "failed"));
        TRANSITIONS.put("waiting_final_review", Set.of("completed", "repairing", "failed"));
        TRANSITIONS.put("repairing", Set.of("keyframe_configuring", "image_generating", "video_clip_generating", "rendering", "failed"));
        TRANSITIONS.put("cancelled", Set.of());
        TRANSITIONS.put("failed", Set.of(
                "analyzing", "asset_analyzing", "plan_generating", "storyboard_generating",
                "image_generating", "video_clip_generating", "script_generating",
                "material_generating", "rendering", "checking", "repairing"
        ));
    }

    /**
     * Maps a failedStage to the retry target status.失败后重试目标
     */
    private static final Map<String, String> RETRY_TARGETS = new HashMap<>();

    static {
        // V1 Core retry targets
        RETRY_TARGETS.put("product_analysis", "analyzing");
        RETRY_TARGETS.put("video_plan", "analyzing");
        RETRY_TARGETS.put("storyboard", "storyboard_generating");
        RETRY_TARGETS.put("material", "material_generating");
        RETRY_TARGETS.put("quality_check", "material_generating");
        RETRY_TARGETS.put("render_manifest", "rendering");

        // Fashion Creative Loop V1 retry targets
        RETRY_TARGETS.put("asset_analysis", "asset_analyzing");
        RETRY_TARGETS.put("reference_analysis", "reference_analyzing");
        RETRY_TARGETS.put("creative_plan", "plan_generating");
        RETRY_TARGETS.put("keyframe", "image_generating");
        RETRY_TARGETS.put("video_clip", "video_clip_generating");
        RETRY_TARGETS.put("qa", "checking");
        RETRY_TARGETS.put("repair", "repairing");
    }

    private static final Set<String> TERMINAL_STATES = Set.of("completed", "exported", "failed", "cancelled");

    private VideoTaskStateMachine() {}

    public static void validateTransition(String current, String target) {
        // Allow cancellation from any non-terminal state
        if ("cancelled".equals(target) && !TERMINAL_STATES.contains(current)) {
            return;
        }
        Set<String> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new InvalidStateTransitionException(current, target);
        }
    }

    /**
     * Determines the retry target status based on the stage where the task failed.
     */
    public static String getRetryTarget(String failedStage) {
        return RETRY_TARGETS.getOrDefault(failedStage, "analyzing");
    }
}

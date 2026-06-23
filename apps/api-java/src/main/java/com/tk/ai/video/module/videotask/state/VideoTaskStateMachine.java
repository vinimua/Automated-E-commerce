package com.tk.ai.video.module.videotask.state;

import com.tk.ai.video.common.InvalidStateTransitionException;

import java.util.Map;
import java.util.Set;

/**
 * Enforces the task state machine from 01-database-schema.sql.
 *
 * Valid transitions:
 *   draft → analyzing
 *   analyzing → analysis_completed
 *   analysis_completed → plan_generated
 *   plan_generated → waiting_plan_selection
 *   waiting_plan_selection → script_generating
 *   script_generating → script_generated
 *   script_generated → material_generating
 *   material_generating → material_generated
 *   material_generated → rendering
 *   rendering → checking
 *   checking → completed
 *   completed → exported
 *   Any in-progress state → failed
 *   failed → retry target (based on failed_stage)
 */
public final class VideoTaskStateMachine {

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("draft", Set.of("analyzing", "failed")),
            Map.entry("analyzing", Set.of("analysis_completed", "failed")),
            Map.entry("analysis_completed", Set.of("plan_generated", "failed")),
            Map.entry("plan_generated", Set.of("waiting_plan_selection", "failed")),
            Map.entry("waiting_plan_selection", Set.of("script_generating", "failed")),
            Map.entry("script_generating", Set.of("script_generated", "failed")),
            Map.entry("script_generated", Set.of("material_generating", "failed")),
            Map.entry("material_generating", Set.of("material_generated", "failed")),
            Map.entry("material_generated", Set.of("rendering", "failed")),
            Map.entry("rendering", Set.of("checking", "failed")),
            Map.entry("checking", Set.of("completed", "failed")),
            Map.entry("completed", Set.of("exported")),
            Map.entry("exported", Set.of()),
            Map.entry("failed", Set.of("analyzing", "script_generating", "material_generating", "rendering", "checking"))
    );

    /**
     * Maps a failedStage to the retry target status.失败后重试目标
     */
    private static final Map<String, String> RETRY_TARGETS = Map.of(
            "product_analysis", "analyzing",
            "video_plan", "analyzing",
            "storyboard", "script_generating",
            "material", "material_generating",
            "quality_check", "material_generating",
            "render_manifest", "rendering"
    );

    private VideoTaskStateMachine() {}

    public static void validateTransition(String current, String target) {
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

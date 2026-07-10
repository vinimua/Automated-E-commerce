package com.tk.ai.video.module.videotask.state;

import com.tk.ai.video.common.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Automated verification of the VideoTaskStateMachine transition matrix.
 *
 * Validates that the Java state machine is self-consistent and matches the
 * documented contract in docs/01-database-schema.sql.
 *
 * This test addresses risk #2 identified in the state governance audit:
 * "Java StateMachine 的转换矩阵没有自动验证".
 */
class VideoTaskStateMachineTest {

    // ── All 29 states from docs/01-database-schema.sql chk_video_tasks_status ──

    private static final Set<String> ALL_DB_STATES = Set.of(
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
    );

    // Terminal states: once reached, no further transitions (except cancelled which is special)
    private static final Set<String> TERMINAL_STATES = Set.of(
        "completed", "exported", "failed", "cancelled"
    );

    // ── Documented transitions from DB schema comments ──

    /**
     * V1 Core path (from DB schema lines 73-78):
     *   draft -> analyzing -> analysis_completed -> plan_generated -> waiting_plan_selection
     *   waiting_plan_selection -> script_generating -> script_generated
     *   script_generated -> material_generating -> material_generated
     *   material_generated -> rendering -> checking -> completed
     *   completed -> exported
     */
    private static final Set<String> V1_CORE_PATH = Set.of(
        "draft", "analyzing", "analysis_completed", "plan_generated",
        "waiting_plan_selection", "script_generating", "script_generated",
        "material_generating", "material_generated", "rendering", "checking",
        "completed", "exported"
    );

    /**
     * Fashion Creative Loop extension (from DB schema lines 80-87):
     *   waiting_storyboard_confirmation -> keyframe_configuring
     *   keyframe_configuring -> image_generating
     *   keyframe_configuring -> waiting_image_confirmation
     *   waiting_image_confirmation -> video_clip_generating
     *   waiting_video_clip_confirmation -> rendering
     *   waiting_final_review -> completed
     *   waiting_final_review -> repairing
     */
    private static final Set<String> FASHION_EXTENSION_STATES = Set.of(
        "asset_uploading", "asset_analyzing", "waiting_asset_confirmation",
        "reference_analyzing", "plan_generating", "storyboard_generating",
        "waiting_storyboard_confirmation", "keyframe_configuring",
        "image_generating", "waiting_image_confirmation",
        "video_clip_generating", "waiting_video_clip_confirmation",
        "waiting_final_review", "repairing"
    );

    // ── Retry target contract (from DB schema + callback contract) ──

    private static Stream<Arguments> retryTargetProvider() {
        return Stream.of(
            Arguments.of("product_analysis",  "analyzing"),
            Arguments.of("video_plan",         "analyzing"),
            Arguments.of("asset_analysis",     "asset_analyzing"),
            Arguments.of("reference_analysis", "reference_analyzing"),
            Arguments.of("creative_plan",      "plan_generating"),
            Arguments.of("storyboard",         "script_generating"),
            Arguments.of("keyframe",           "image_generating"),
            Arguments.of("video_clip",         "video_clip_generating"),
            Arguments.of("material",           "material_generating"),
            Arguments.of("quality_check",      "material_generating"),
            Arguments.of("render_manifest",    "rendering"),
            Arguments.of("qa",                 "checking"),
            Arguments.of("repair",             "repairing")
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. Every DB state has a transition entry
    // ═══════════════════════════════════════════════════════════════

    @Test
    void everyDbStateIsCoveredByTransitionMap() {
        for (String state : ALL_DB_STATES) {
            // Every state must either be a valid source for transitions
            // or be reachable as a target. We verify each state does NOT
            // throw an "unknown source" error when validated.
            boolean isValidSource = doesTransitionMapContainSource(state);

            // At minimum, every state must be in the transition map as a source
            // or be reachable as a transition target from some source
            assertThat(isValidSource || isStateReachableAsTarget(state))
                .as("State '%s' is neither in the transition map nor reachable as a target", state)
                .isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("validTransitionProvider")
    void allowsDocumentedTransition(String from, String to) {
        assertDoesNotThrow(
            () -> VideoTaskStateMachine.validateTransition(from, to),
            "Documented transition %s -> %s should be allowed"
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Retry targets are all valid states
    // ═══════════════════════════════════════════════════════════════

    @ParameterizedTest
    @MethodSource("retryTargetProvider")
    void retryTargetMapsToValidState(String failedStage, String expectedRetryTarget) {
        String actual = VideoTaskStateMachine.getRetryTarget(failedStage);
        assertThat(actual)
            .as("Retry target for failedStage='%s'", failedStage)
            .isEqualTo(expectedRetryTarget);

        // The retry target must be a valid DB state
        assertThat(ALL_DB_STATES)
            .as("Retry target '%s' for stage '%s' is not a valid DB state", actual, failedStage)
            .contains(actual);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Unknown retry stage falls back to a valid state
    // ═══════════════════════════════════════════════════════════════

    @Test
    void unknownFailedStageFallsBackToAnalyzing() {
        String fallback = VideoTaskStateMachine.getRetryTarget("nonexistent_stage_xyz");
        assertThat(fallback).isEqualTo("analyzing");
        assertThat(ALL_DB_STATES).contains(fallback);
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Hard terminal states (exported, cancelled) reject ALL transitions.
    //    "completed" only allows → exported.
    // ═══════════════════════════════════════════════════════════════

    @Test
    void completedOnlyAllowsTransitionToExported() {
        // completed → exported is the only valid transition
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("completed", "exported"));

        // All other targets are rejected
        for (String target : ALL_DB_STATES) {
            if (target.equals("exported") || target.equals("completed") || target.equals("cancelled")) {
                continue;
            }
            assertThatThrownBy(() -> VideoTaskStateMachine.validateTransition("completed", target))
                .as("'completed' should NOT allow transition to '%s'", target)
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void exportedRejectsAllTransitions() {
        for (String target : ALL_DB_STATES) {
            if (target.equals("exported")) continue;
            assertThatThrownBy(() -> VideoTaskStateMachine.validateTransition("exported", target))
                .as("'exported' should not allow transition to '%s'", target)
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void cancelledRejectsAllTransitions() {
        // cancelled cannot transition to anything — it is a hard terminal state
        for (String target : ALL_DB_STATES) {
            if (target.equals("cancelled")) continue;
            assertThatThrownBy(() -> VideoTaskStateMachine.validateTransition("cancelled", target))
                .as("'cancelled' should not allow transition to '%s'", target)
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4b. "failed" is a soft terminal — it allows retry transitions
    //     to specific regeneration states but rejects unrelated transitions
    // ═══════════════════════════════════════════════════════════════

    private static final Set<String> FAILED_RETRY_TARGETS = Set.of(
        "analyzing", "asset_analyzing", "plan_generating", "storyboard_generating",
        "image_generating", "video_clip_generating", "script_generating",
        "material_generating", "rendering", "checking", "repairing"
    );

    @Test
    void failedAllowsOnlyRetryTransitions() {
        for (String target : ALL_DB_STATES) {
            if (FAILED_RETRY_TARGETS.contains(target) || target.equals("failed") || target.equals("cancelled")) {
                // These are valid transitions — cancellation is a separate path
                continue;
            }
            assertThatThrownBy(() -> VideoTaskStateMachine.validateTransition("failed", target))
                .as("'failed' should NOT allow transition to '%s' (not a retry target)", target)
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void failedAllowsRetryTransitions() {
        for (String target : FAILED_RETRY_TARGETS) {
            final String t = target;
            assertDoesNotThrow(
                () -> VideoTaskStateMachine.validateTransition("failed", t),
                () -> String.format("'failed' should allow retry transition to '%s'", t)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Cancellation is allowed from any non-terminal state
    // ═══════════════════════════════════════════════════════════════

    @Test
    void cancelFromAnyNonTerminalStateIsAllowed() {
        for (String state : ALL_DB_STATES) {
            if (TERMINAL_STATES.contains(state)) {
                continue; // can't cancel an already-terminal task
            }
            String from = state;
            assertDoesNotThrow(
                () -> VideoTaskStateMachine.validateTransition(from, "cancelled"),
                "Should allow cancellation from non-terminal state '%s'"
            );
        }
    }

    @Test
    void cancelFromTerminalStateIsRejected() {
        for (String terminal : TERMINAL_STATES) {
            assertThatThrownBy(() -> VideoTaskStateMachine.validateTransition(terminal, "cancelled"))
                .as("Should reject cancellation from terminal state '%s'", terminal)
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. transition to failed is allowed from most in-progress states
    // ═══════════════════════════════════════════════════════════════

    @Test
    void failedIsAllowedFromInProgressStates() {
        Set<String> inProgressStates = Set.of(
            "draft", "analyzing", "analysis_completed", "plan_generated",
            "waiting_plan_selection", "script_generating", "script_generated",
            "material_generating", "material_generated", "rendering", "checking",
            "asset_uploading", "asset_analyzing", "waiting_asset_confirmation",
            "reference_analyzing", "plan_generating", "storyboard_generating",
            "waiting_storyboard_confirmation", "keyframe_configuring",
            "image_generating", "waiting_image_confirmation",
            "video_clip_generating", "waiting_video_clip_confirmation",
            "waiting_final_review", "repairing"
        );

        for (String state : inProgressStates) {
            assertDoesNotThrow(
                () -> VideoTaskStateMachine.validateTransition(state, "failed"),
                "Should allow transition to 'failed' from in-progress state '%s'"
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Key Fashion Creative Loop transitions are enforced
    // ═══════════════════════════════════════════════════════════════

    @Test
    void repairTransitionsBackToRegenerationStates() {
        // repairing → image_generating (regenerate keyframes)
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("repairing", "image_generating"));

        // repairing → video_clip_generating (regenerate clips)
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("repairing", "video_clip_generating"));

        // repairing → rendering (re-render)
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("repairing", "rendering"));

        // repairing → keyframe_configuring (reconfigure keyframes)
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("repairing", "keyframe_configuring"));
    }

    @Test
    void keyframeConfiguringAllowsBothAiGenerationAndDirectUpload() {
        // AI generation path
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("keyframe_configuring", "image_generating"));

        // User direct upload path (skip AI)
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("keyframe_configuring", "waiting_image_confirmation"));
    }

    @Test
    void waitingFinalReviewBranchesToCompletedOrRepairing() {
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("waiting_final_review", "completed"));

        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("waiting_final_review", "repairing"));
    }

    @Test
    void waitingPlanSelectionBranchesToScriptOrStoryboard() {
        // V1 path: script generation
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("waiting_plan_selection", "script_generating"));

        // Fashion V2 path: storyboard generation
        assertDoesNotThrow(() ->
            VideoTaskStateMachine.validateTransition("waiting_plan_selection", "storyboard_generating"));
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Invalid transitions are properly rejected
    // ═══════════════════════════════════════════════════════════════

    @Test
    void rejectsJumpingOverIntermediateStates() {
        // Can't jump from draft directly to completed
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("draft", "completed"))
            .isInstanceOf(InvalidStateTransitionException.class);

        // Can't jump from analyzing to rendering
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("analyzing", "rendering"))
            .isInstanceOf(InvalidStateTransitionException.class);

        // Can't go backwards from completed to draft
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("completed", "draft"))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void rejectsTransitionToNonexistentState() {
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("draft", "nonexistent_state"))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void rejectsTransitionFromNonexistentState() {
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("nonexistent_state", "draft"))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. All states from DB schema are covered by union of V1 + Fashion sets
    // ═══════════════════════════════════════════════════════════════

    @Test
    void allStatesAccountedForInV1OrFashionSets() {
        Set<String> accountedFor = new java.util.HashSet<>();
        accountedFor.addAll(V1_CORE_PATH);
        accountedFor.addAll(FASHION_EXTENSION_STATES);
        accountedFor.add("cancelled");
        accountedFor.add("failed");

        for (String state : ALL_DB_STATES) {
            assertThat(accountedFor)
                .as("State '%s' is not assigned to either V1_CORE_PATH or FASHION_EXTENSION_STATES", state)
                .contains(state);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. InvalidStateTransitionException carries useful message
    // ═══════════════════════════════════════════════════════════════

    @Test
    void exceptionMessageContainsStateNames() {
        assertThatThrownBy(() ->
            VideoTaskStateMachine.validateTransition("draft", "completed"))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("draft")
            .hasMessageContaining("completed");
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean doesTransitionMapContainSource(String state) {
        try {
            // validateTransition will throw for unknown source states
            // with a message containing the state name
            VideoTaskStateMachine.validateTransition(state, "failed");
            return true;
        } catch (InvalidStateTransitionException e) {
            // If the message mentions the source state, it's a known source
            // but the transition itself is invalid
            return e.getMessage().contains(state);
        }
    }

    private boolean isStateReachableAsTarget(String candidate) {
        // Check if any valid source can transition to this state
        for (String source : ALL_DB_STATES) {
            try {
                VideoTaskStateMachine.validateTransition(source, candidate);
                return true; // found a valid transition
            } catch (InvalidStateTransitionException ignored) {
                // not reachable from this source, try next
            }
        }
        return false;
    }

    private static Stream<Arguments> validTransitionProvider() {
        return Stream.of(
            // V1 Core
            Arguments.of("draft", "analyzing"),
            Arguments.of("analyzing", "analysis_completed"),
            Arguments.of("analysis_completed", "plan_generated"),
            Arguments.of("plan_generated", "waiting_plan_selection"),
            Arguments.of("waiting_plan_selection", "script_generating"),
            Arguments.of("script_generating", "script_generated"),
            Arguments.of("script_generated", "material_generating"),
            Arguments.of("material_generating", "material_generated"),
            Arguments.of("material_generated", "rendering"),
            Arguments.of("rendering", "checking"),
            Arguments.of("checking", "completed"),
            Arguments.of("completed", "exported"),

            // Fashion Creative Loop
            Arguments.of("draft", "asset_uploading"),
            Arguments.of("asset_uploading", "asset_analyzing"),
            Arguments.of("asset_analyzing", "waiting_asset_confirmation"),
            Arguments.of("waiting_asset_confirmation", "reference_analyzing"),
            Arguments.of("waiting_asset_confirmation", "plan_generating"),
            Arguments.of("reference_analyzing", "plan_generating"),
            Arguments.of("plan_generating", "waiting_plan_selection"),
            Arguments.of("waiting_plan_selection", "storyboard_generating"),
            Arguments.of("storyboard_generating", "waiting_storyboard_confirmation"),
            Arguments.of("waiting_storyboard_confirmation", "keyframe_configuring"),
            Arguments.of("keyframe_configuring", "image_generating"),
            Arguments.of("keyframe_configuring", "waiting_image_confirmation"),
            Arguments.of("image_generating", "waiting_image_confirmation"),
            Arguments.of("waiting_image_confirmation", "image_generating"),
            Arguments.of("waiting_image_confirmation", "video_clip_generating"),
            Arguments.of("video_clip_generating", "waiting_video_clip_confirmation"),
            Arguments.of("waiting_video_clip_confirmation", "rendering"),
            Arguments.of("waiting_final_review", "completed"),
            Arguments.of("waiting_final_review", "repairing"),

            // Repair cycle
            Arguments.of("repairing", "image_generating"),
            Arguments.of("repairing", "video_clip_generating"),
            Arguments.of("repairing", "rendering"),
            Arguments.of("repairing", "keyframe_configuring")
        );
    }
}

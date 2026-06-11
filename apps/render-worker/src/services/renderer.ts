/**
 * Remotion-based video renderer.
 *
 * V1: 3 templates (pain_point_solution_v1, before_after_v1, review_v1).
 * Full implementation in Phase 5.
 */

export interface RenderResult {
  outputPath: string;
  coverPath: string;
  duration: number;
  renderTimeMs: number;
}

export async function renderVideo(
  manifest: Record<string, unknown>
): Promise<RenderResult> {
  const template = (manifest.template as string) || "pain_point_solution_v1";

  console.log(`[renderer] Rendering with template: ${template}`);

  // TODO: Phase 5 — actual Remotion rendering
  // 1. Bundle the Remotion project
  // 2. Select composition by template
  // 3. Render frames
  // 4. FFmpeg transcode to MP4 h264
  // 5. Extract cover frame
  //
  // Placeholder for Phase 5:
  return {
    outputPath: "/tmp/render-output/placeholder.mp4",
    coverPath: "/tmp/render-output/placeholder-cover.jpg",
    duration: (manifest.duration as number) || 15,
    renderTimeMs: 0,
  };
}

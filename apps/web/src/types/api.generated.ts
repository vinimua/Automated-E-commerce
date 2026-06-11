// Placeholder — will be replaced by openapi-typescript generation.
// Run: npx openapi-typescript ../../docs/02-openapi-spec.yaml -o ./src/types/api.generated.ts

export interface components {
  schemas: {
    VideoType: "pain_point_solution" | "before_after" | "review" | "product_showcase" | "ugc_style" | "tutorial";
    VideoTaskStatus:
      | "draft"
      | "analyzing"
      | "analysis_completed"
      | "plan_generated"
      | "waiting_plan_selection"
      | "script_generating"
      | "script_generated"
      | "material_generating"
      | "material_generated"
      | "rendering"
      | "checking"
      | "completed"
      | "failed"
      | "exported";
    VideoStatus: "completed" | "exported" | "deleted";
    Product: Record<string, unknown>;
    VideoTask: Record<string, unknown>;
    VideoPlan: Record<string, unknown>;
    Storyboard: Record<string, unknown>;
    StoryboardShot: Record<string, unknown>;
    Video: Record<string, unknown>;
    ErrorDetail: Record<string, unknown>;
  };
}

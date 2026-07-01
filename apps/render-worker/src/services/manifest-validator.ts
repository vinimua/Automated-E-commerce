import { ZodIssue } from "zod";
import { RenderManifestSchema } from "../schemas/render-manifest.schema";

export interface ValidationResult {
  valid: boolean;
  error?: string;
  details?: Record<string, unknown>;
}

export async function validateRenderManifest(manifest: unknown): Promise<ValidationResult> {
  const result = RenderManifestSchema.safeParse(manifest);
  if (result.success) {
    return { valid: true };
  }

  const firstIssue = result.error.issues[0];
  return {
    valid: false,
    error: formatIssue(firstIssue),
    details: {
      issues: result.error.issues.map((issue) => ({
        path: issue.path.join("."),
        message: issue.message,
        code: issue.code,
      })),
    },
  };
}

function formatIssue(issue: ZodIssue | undefined): string {
  if (!issue) return "Manifest validation failed";
  const path = issue.path.length > 0 ? issue.path.join(".") : "manifest";
  return `${path}: ${issue.message}`;
}

/**
 * Transitions — render transition effects between shots.
 * V1 simplified implementation: only fade and slide_up.
 * quick_cut is implicit (no transition component needed).
 */
import React from "react";
import { useCurrentFrame, interpolate } from "remotion";

interface TransitionProps {
  type: string;       // transition type
  durationFrames: number;
  children: React.ReactNode;
}

/**
 * Wraps shot content with a transition effect at the start of the shot.
 * The transition runs over the first 15 frames (0.5s at 30fps).
 */
export const TransitionWrapper: React.FC<TransitionProps> = ({
  type,
  durationFrames,
  children,
}) => {
  const frame = useCurrentFrame();

  if (type === "none" || type === "quick_cut") {
    return <>{children}</>;
  }

  if (type === "fade") {
    const opacity = interpolate(
      frame,
      [0, Math.min(15, durationFrames * 0.3)],
      [0, 1],
      { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
    );
    return <div style={{ opacity, width: "100%", height: "100%" }}>{children}</div>;
  }

  if (type === "slide_up") {
    const slideAmount = interpolate(
      frame,
      [0, Math.min(15, durationFrames * 0.3)],
      [120, 0],
      { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
    );
    const opacity = interpolate(
      frame,
      [0, Math.min(15, durationFrames * 0.3)],
      [0, 1],
      { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
    );
    return (
      <div
        style={{
          transform: `translateY(${slideAmount}px)`,
          opacity,
          width: "100%",
          height: "100%",
        }}
      >
        {children}
      </div>
    );
  }

  // Fallback for other transitions: just render content
  return <>{children}</>;
};

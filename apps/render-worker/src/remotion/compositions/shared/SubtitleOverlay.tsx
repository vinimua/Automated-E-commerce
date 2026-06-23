/**
 * SubtitleOverlay — renders subtitle text at configurable position
 * with optional semi-transparent background and fade in/out animation.
 */
import React from "react";
import { useCurrentFrame, interpolate } from "remotion";

export interface SubtitleStyle {
  fontSize: number;          // 36-72
  position: string;          // bottom_center | middle_center | top_center
  maxLines: number;          // 1 | 2
  background: string;        // none | semi_transparent | solid
  safeAreaBottom?: number;   // px from bottom (default 180)
}

interface SubtitleOverlayProps {
  text: string;
  style: SubtitleStyle;
  durationInFrames: number;
}

export const SubtitleOverlay: React.FC<SubtitleOverlayProps> = ({
  text,
  style,
  durationInFrames,
}) => {
  const frame = useCurrentFrame();

  // Fade in over first 15% of the shot, fade out over last 20%
  const fadeIn = interpolate(frame, [0, Math.floor(durationInFrames * 0.15)], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const fadeOut = interpolate(
    frame,
    [Math.floor(durationInFrames * 0.8), durationInFrames],
    [1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
  );
  const opacity = fadeIn * fadeOut;

  // Position
  const topOffset = (() => {
    switch (style.position) {
      case "top_center": return 60;
      case "middle_center": return 1920 / 2 - 80;
      case "bottom_center":
      default: return 1920 - (style.safeAreaBottom || 180) - 80;
    }
  })();

  // Background
  const bgOpacity = style.background === "semi_transparent" ? 0.55
    : style.background === "solid" ? 0.85
    : 0;

  const lineHeight = style.fontSize * 1.3;
  const lines = text.split("\n").slice(0, style.maxLines);
  const totalHeight = lines.length * lineHeight + 32;

  return (
    <div
      style={{
        position: "absolute",
        top: topOffset - totalHeight / 2,
        left: "50%",
        transform: "translateX(-50%)",
        maxWidth: 900,
        width: "100%",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "16px 32px",
        borderRadius: 12,
        backgroundColor: bgOpacity > 0
          ? `rgba(0, 0, 0, ${bgOpacity})`
          : "transparent",
        opacity,
      }}
    >
      {lines.map((line, i) => (
        <span
          key={i}
          style={{
            fontSize: style.fontSize,
            lineHeight: `${lineHeight}px`,
            color: "#ffffff",
            fontFamily: "Arial, sans-serif",
            fontWeight: 700,
            textAlign: "center",
            textShadow: "0 2px 8px rgba(0,0,0,0.7)",
            userSelect: "none",
          }}
        >
          {line}
        </span>
      ))}
    </div>
  );
};

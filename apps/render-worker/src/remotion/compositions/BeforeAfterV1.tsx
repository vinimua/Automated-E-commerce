/**
 * Remotion Template: before_after_v1
 *
 * Time structure (from 04-render-manifest-schema.md):
 *   0-2s   → Show result             (shot 1)
 *   2-6s   → Before state            (shot 2)
 *   6-14s  → Process / usage         (shots 3-4)
 *   14-20s → Before/after comparison (shot 5)
 *   20-25s → Product close-up        (shot 6)
 */
import React from "react";
import { Sequence, useCurrentFrame } from "remotion";
import { AssetRenderer } from "./shared/AssetRenderer";
import { SubtitleOverlay } from "./shared/SubtitleOverlay";
import { TransitionWrapper } from "./shared/Transitions";
import type { RenderAsset } from "./shared/AssetRenderer";

interface DownloadedAsset {
  shotNo: number;
  localPath: string;
}

interface BeforeAfterProps {
  duration: number;
  fps: number;
  assets: RenderAsset[];
  subtitleStyle: {
    fontSize: number;
    position: string;
    maxLines: number;
    background: string;
    safeAreaBottom?: number;
  };
  downloadedAssets: DownloadedAsset[];
}

export const BeforeAfterV1: React.FC<BeforeAfterProps> = ({
  duration,
  fps,
  assets,
  subtitleStyle,
  downloadedAssets,
}) => {
  // Build cumulative frame offsets from asset durations
  const sequences: { asset: RenderAsset; from: number; durationFrames: number }[] = [];
  let cumulativeFrames = 0;
  for (const asset of assets) {
    const durationFrames = asset.duration * fps;
    sequences.push({ asset, from: cumulativeFrames, durationFrames });
    cumulativeFrames += durationFrames;
  }

  return (
    <div style={{ width: 1080, height: 1920, backgroundColor: "#000000", position: "relative" }}>
      {sequences.map(({ asset, from, durationFrames }) => (
        <Sequence key={asset.shotNo} from={from} durationInFrames={durationFrames}>
          <TransitionWrapper type={asset.edit.transition} durationFrames={durationFrames}>
            <AssetRenderer asset={asset} downloadedAssets={downloadedAssets} />
          </TransitionWrapper>
          {asset.subtitle && (
            <SubtitleOverlay
              text={asset.subtitle}
              style={subtitleStyle}
              durationInFrames={durationFrames}
            />
          )}
        </Sequence>
      ))}
    </div>
  );
};

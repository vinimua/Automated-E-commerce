/**
 * Remotion Template: review_v1
 *
 * Time structure (from 04-render-manifest-schema.md):
 *   0-3s   → Problem / question  (shot 1)
 *   3-8s   → Show product        (shot 2, product_image)
 *   8-18s  → Test process        (shots 3-5)
 *   18-23s → Test results        (shot 6)
 *   23-30s → Recommendation      (shot 7)
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

interface ReviewProps {
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

export const ReviewV1: React.FC<ReviewProps> = ({
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

/**
 * Remotion Template: pain_point_solution_v1
 *
 * Time structure (from 04-render-manifest-schema.md):
 *   0-3s   → Pain point hook       (shot 1)
 *   3-7s   → Product appears        (shot 2, product_image)
 *   7-14s  → Solution process       (shots 3-4)
 *   14-19s → Results showcase       (shot 5)
 *   19-22s → Product close-up + CTA (shot 6)
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

interface ManifestProps {
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
}

interface PainPointSolutionProps {
  duration: number;
  fps: number;
  assets: RenderAsset[];
  subtitleStyle: ManifestProps["subtitleStyle"];
  downloadedAssets: DownloadedAsset[];
}

export const PainPointSolutionV1: React.FC<PainPointSolutionProps> = ({
  duration,
  fps,
  assets,
  subtitleStyle,
  downloadedAssets,
}) => {
  const frame = useCurrentFrame();

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

/**
 * AssetRenderer — dispatches asset rendering by type (image / video / product_image / text).
 * Uses local file paths from the downloadedAssets map.
 */
import React from "react";
import { Img, Video, useCurrentFrame, interpolate, AbsoluteFill } from "remotion";

export interface RenderAsset {
  shotNo: number;
  type: "image" | "video" | "product_image" | "text";
  url?: string;
  textContent?: string;
  duration: number;
  subtitle?: string;
  edit: {
    transition: string;
    zoom: string;
    position: string;
    crop: "cover" | "contain";
  };
}

interface DownloadedAsset {
  shotNo: number;
  type?: string;
  localPath: string;
}

interface AssetRendererProps {
  asset: RenderAsset;
  downloadedAssets: DownloadedAsset[];
}

export const AssetRenderer: React.FC<AssetRendererProps> = ({
  asset,
  downloadedAssets,
}) => {
  const frame = useCurrentFrame();
  const durationFrames = asset.duration * 30; // 30fps

  // Find local path for this asset
  const local = downloadedAssets.find((d) => d.shotNo === asset.shotNo);
  const localPath = local?.localPath || asset.url || "";
  const renderType = local?.type || asset.type;

  // Zoom animation
  const zoomScale = (() => {
    switch (asset.edit?.zoom) {
      case "slow_in": return interpolate(frame, [0, durationFrames], [1, 1.08], { extrapolateRight: "clamp" });
      case "slow_out": return interpolate(frame, [0, durationFrames], [1.08, 1], { extrapolateLeft: "clamp" });
      case "pulse": return 1 + Math.sin(frame * 0.1) * 0.03;
      case "fast_in": return interpolate(frame, [0, durationFrames * 0.5], [1, 1.12], { extrapolateRight: "clamp" });
      default: return 1;
    }
  })();

  const objectFit = asset.edit?.crop === "contain" ? "contain" : "cover";

  switch (renderType) {
    case "image":
      return (
        <div style={{ width: "100%", height: "100%", overflow: "hidden", backgroundColor: "#1a1a1a" }}>
          <Img
            src={localPath}
            style={{
              width: "100%",
              height: "100%",
              objectFit,
              transform: `scale(${zoomScale})`,
            }}
          />
        </div>
      );

    case "video":
      return (
        <div style={{ width: "100%", height: "100%", overflow: "hidden", backgroundColor: "#1a1a1a" }}>
          <Video
            src={localPath}
            style={{
              width: "100%",
              height: "100%",
              objectFit,
              transform: `scale(${zoomScale})`,
            }}
            // loop within this shot's sequence
          />
        </div>
      );

    case "product_image":
      return (
        <div
          style={{
            width: "100%",
            height: "100%",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            background: "linear-gradient(180deg, #f5f5f7 0%, #e8e8ed 100%)",
            padding: 80,
          }}
        >
          <Img
            src={localPath}
            style={{
              maxWidth: "80%",
              maxHeight: "70%",
              objectFit: "contain",
              transform: `scale(${zoomScale})`,
              borderRadius: 8,
              boxShadow: "0 20px 60px rgba(0,0,0,0.15)",
            }}
          />
        </div>
      );

    case "text":
      return (
        <AbsoluteFill
          style={{
            backgroundColor: "#2d2d30",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 60,
          }}
        >
          <h1
            style={{
              fontSize: 56,
              fontWeight: 800,
              color: "#ffffff",
              textAlign: "center",
              fontFamily: "Arial, sans-serif",
              lineHeight: 1.3,
              maxWidth: 850,
              transform: `scale(${zoomScale})`,
            }}
          >
            {asset.textContent || ""}
          </h1>
        </AbsoluteFill>
      );

    default:
      return (
        <div style={{ width: "100%", height: "100%", backgroundColor: "#2d2d30" }} />
      );
  }
};

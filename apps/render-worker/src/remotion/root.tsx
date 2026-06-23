/**
 * Remotion root — entry point for @remotion/bundler.
 * Registers all V1 template compositions with registerRoot().
 */
import React from "react";
import { registerRoot, Composition } from "remotion";
import { PainPointSolutionV1 } from "./compositions/PainPointSolutionV1";
import { BeforeAfterV1 } from "./compositions/BeforeAfterV1";
import { ReviewV1 } from "./compositions/ReviewV1";

const DEFAULT_PROPS = {
  duration: 22,
  fps: 30,
  assets: [],
  subtitleStyle: {
    fontSize: 48,
    position: "bottom_center",
    maxLines: 1,
    background: "semi_transparent",
    safeAreaBottom: 180,
  },
  downloadedAssets: [],
};

function calculateVideoMetadata({ props }: { props: Record<string, unknown> }) {
  const fps = typeof props.fps === "number" ? props.fps : 30;
  const duration = typeof props.duration === "number" ? props.duration : 22;
  return {
    durationInFrames: Math.max(1, Math.round(duration * fps)),
    fps,
  };
}

const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="PainPointSolutionV1"
        component={PainPointSolutionV1 as unknown as React.FC<Record<string, unknown>>}
        durationInFrames={DEFAULT_PROPS.duration * DEFAULT_PROPS.fps}
        calculateMetadata={calculateVideoMetadata}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={DEFAULT_PROPS}
      />
      <Composition
        id="BeforeAfterV1"
        component={BeforeAfterV1 as unknown as React.FC<Record<string, unknown>>}
        durationInFrames={DEFAULT_PROPS.duration * DEFAULT_PROPS.fps}
        calculateMetadata={calculateVideoMetadata}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={DEFAULT_PROPS}
      />
      <Composition
        id="ReviewV1"
        component={ReviewV1 as unknown as React.FC<Record<string, unknown>>}
        durationInFrames={DEFAULT_PROPS.duration * DEFAULT_PROPS.fps}
        calculateMetadata={calculateVideoMetadata}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={DEFAULT_PROPS}
      />
    </>
  );
};

registerRoot(RemotionRoot);

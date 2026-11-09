import {
  AbsoluteFill,
  Easing,
  Img,
  Interactive,
  interpolate,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { gold, ink, sans, serif } from "./theme";

export const HookScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <AbsoluteFill
      name="Hook"
      style={{
        backgroundColor: ink,
        justifyContent: "center",
        alignItems: "center",
      }}
    >
      <Img
        name="Eiffel logo"
        src={staticFile("launch-logo.png")}
        style={{
          position: "absolute",
          top: 260,
          width: 360,
          opacity: interpolate(frame, [0, 0.6 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [0, 2.5 * fps], [1.25, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
        }}
      />
      <Interactive.Div
        name="Headline"
        style={{
          position: "absolute",
          top: 1080,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 150,
          lineHeight: 1.05,
          color: "white",
          opacity: interpolate(frame, [0.7 * fps, 1.4 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [0.7 * fps, 1.6 * fps], ["0px 60px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        See Paris like a local
      </Interactive.Div>
      <Interactive.Div
        name="Subline"
        style={{
          position: "absolute",
          top: 1440,
          fontFamily: sans,
          fontWeight: 500,
          fontSize: 48,
          color: gold,
          opacity: interpolate(frame, [1.3 * fps, 1.9 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        The insider’s guide, in your pocket
      </Interactive.Div>
    </AbsoluteFill>
  );
};

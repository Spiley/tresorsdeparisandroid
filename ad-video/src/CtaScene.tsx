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

export const CtaScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <AbsoluteFill
      name="CTA"
      style={{
        backgroundColor: ink,
        background: `radial-gradient(circle at 50% 38%, #2A2214 0%, ${ink} 60%)`,
        alignItems: "center",
      }}
    >
      <Img
        name="App icon"
        src={staticFile("app-icon.png")}
        style={{
          position: "absolute",
          top: 430,
          width: 400,
          height: 400,
          borderRadius: 90,
          boxShadow: "0 40px 120px rgba(212,169,74,0.35)",
          scale: interpolate(frame, [0, 0.8 * fps], [0.6, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 12 }),
            output: "perceptual-scale",
          }),
          opacity: interpolate(frame, [0, 0.3 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      />
      <Interactive.Div
        name="App name"
        style={{
          position: "absolute",
          top: 920,
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 120,
          color: "white",
          opacity: interpolate(frame, [0.4 * fps, 0.9 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Trésors de Paris
      </Interactive.Div>
      <Interactive.Div
        name="Tagline"
        style={{
          position: "absolute",
          top: 1090,
          fontFamily: sans,
          fontWeight: 500,
          fontSize: 48,
          color: "rgba(255,255,255,0.75)",
          opacity: interpolate(frame, [0.6 * fps, 1.1 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Your insider’s guide to Paris
      </Interactive.Div>
      <Interactive.Div
        name="Download button"
        style={{
          position: "absolute",
          top: 1330,
          padding: "36px 80px",
          borderRadius: 100,
          background: gold,
          fontFamily: sans,
          fontWeight: 700,
          fontSize: 50,
          color: "white",
          opacity: interpolate(frame, [1 * fps, 1.4 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [1 * fps, 1.5 * fps, 2.2 * fps, 2.5 * fps], [0.85, 1, 1, 1.05], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
        }}
      >
        Download the free app
      </Interactive.Div>
      <Interactive.Div
        name="Platforms"
        style={{
          position: "absolute",
          top: 1520,
          fontFamily: sans,
          fontWeight: 600,
          fontSize: 40,
          letterSpacing: 2,
          color: gold,
          opacity: interpolate(frame, [1.3 * fps, 1.8 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        iPhone · Android
      </Interactive.Div>
    </AbsoluteFill>
  );
};

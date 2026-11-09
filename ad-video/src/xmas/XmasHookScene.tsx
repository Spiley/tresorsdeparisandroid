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
import { lightGold, sans, serif } from "../theme";

export const XmasHookScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <AbsoluteFill name="Hook" style={{ backgroundColor: "black", alignItems: "center", overflow: "hidden" }}>
      {/* Avenue Montaigne in Christmas lights, softened into bokeh behind the title */}
      <Img
        name="Christmas lights"
        src={staticFile("xmas/featured.jpg")}
        style={{
          position: "absolute",
          height: 1920,
          width: 4110,
          maxWidth: "none",
          left: interpolate(frame, [0, 3 * fps], [-700, -900], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          filter: "blur(5px) brightness(0.75)",
          scale: interpolate(frame, [0, 3 * fps], [1.15, 1.05], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            output: "perceptual-scale",
          }),
        }}
      />
      <AbsoluteFill style={{ background: "radial-gradient(circle at 50% 50%, rgba(0,0,0,0.15), rgba(0,0,0,0.7))" }} />
      <Interactive.Div
        name="Kicker"
        style={{
          position: "absolute",
          top: 640,
          fontFamily: sans,
          fontWeight: 700,
          fontSize: 44,
          letterSpacing: 14,
          color: lightGold,
          opacity: interpolate(frame, [0.2 * fps, 0.7 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        HOLIDAY GUIDE
      </Interactive.Div>
      <Interactive.Div
        name="Title"
        style={{
          position: "absolute",
          top: 730,
          width: 940,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 168,
          lineHeight: 1,
          color: "white",
          textShadow: "0 6px 40px rgba(0,0,0,0.6)",
          opacity: interpolate(frame, [0.4 * fps, 1 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [0.4 * fps, 1.4 * fps], [0.9, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
        }}
      >
        Christmas in Paris
      </Interactive.Div>
      <Interactive.Div
        name="Subtitle"
        style={{
          position: "absolute",
          top: 1120,
          fontFamily: sans,
          fontWeight: 600,
          fontSize: 50,
          color: "white",
          opacity: interpolate(frame, [1.2 * fps, 1.7 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Markets · Windows · Crèches
      </Interactive.Div>
    </AbsoluteFill>
  );
};

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
import { gold, lightGold, sans, serif } from "../theme";

export const XmasCtaScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <AbsoluteFill
      name="CTA"
      style={{
        backgroundColor: "black",
        background: "radial-gradient(circle at 50% 35%, #3A2A10 0%, #000 65%)",
        alignItems: "center",
      }}
    >
      <Interactive.Div
        name="Headline"
        style={{
          position: "absolute",
          top: 260,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 112,
          lineHeight: 1.04,
          color: "white",
          opacity: interpolate(frame, [0, 0.5 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [0, 0.8 * fps], ["0px 40px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        Plan your Christmas in Paris
      </Interactive.Div>
      <Interactive.Div
        name="Subline"
        style={{
          position: "absolute",
          top: 720,
          fontFamily: sans,
          fontWeight: 500,
          fontSize: 50,
          color: "rgba(255,255,255,0.8)",
          opacity: interpolate(frame, [0.4 * fps, 0.9 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Read the full holiday guide on
      </Interactive.Div>
      <Interactive.Div
        name="Website button"
        style={{
          position: "absolute",
          top: 820,
          padding: "38px 70px",
          borderRadius: 100,
          background: gold,
          fontFamily: sans,
          fontWeight: 700,
          fontSize: 58,
          color: "white",
          boxShadow: "0 20px 80px rgba(212,169,74,0.45)",
          opacity: interpolate(frame, [0.8 * fps, 1.1 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          scale: interpolate(frame, [0.8 * fps, 1.3 * fps], [0.8, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.spring({ damping: 12 }),
            output: "perceptual-scale",
          }),
        }}
      >
        tresors-de-paris.com
      </Interactive.Div>
      <Interactive.Div
        name="Or the app"
        style={{
          position: "absolute",
          top: 1110,
          fontFamily: sans,
          fontWeight: 600,
          fontSize: 44,
          letterSpacing: 4,
          color: lightGold,
          opacity: interpolate(frame, [1.6 * fps, 2 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        OR IN THE FREE APP
      </Interactive.Div>
      <Interactive.Div
        name="App row"
        style={{
          position: "absolute",
          top: 1220,
          display: "flex",
          alignItems: "center",
          gap: 40,
          opacity: interpolate(frame, [1.8 * fps, 2.3 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [1.8 * fps, 2.5 * fps], ["0px 50px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        <Img src={staticFile("app-icon.png")} style={{ width: 220, height: 220, borderRadius: 50 }} />
        <div>
          <div style={{ fontFamily: serif, fontWeight: 800, fontSize: 76, color: "white" }}>Trésors de Paris</div>
          <div style={{ fontFamily: sans, fontWeight: 600, fontSize: 44, color: "rgba(255,255,255,0.75)", marginTop: 6 }}>
            iPhone · Android
          </div>
        </div>
      </Interactive.Div>
    </AbsoluteFill>
  );
};

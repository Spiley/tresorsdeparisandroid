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

export const FavoritesScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const saved = frame >= 1 * fps;

  return (
    <AbsoluteFill name="Favorites" style={{ backgroundColor: ink, alignItems: "center" }}>
      <Interactive.Div
        name="Headline"
        style={{
          position: "absolute",
          top: 200,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 112,
          lineHeight: 1.05,
          color: "white",
        }}
      >
        Save it.
      </Interactive.Div>
      <Interactive.Div
        name="Headline 2"
        style={{
          position: "absolute",
          top: 330,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 112,
          lineHeight: 1.05,
          color: gold,
          opacity: interpolate(frame, [1.6 * fps, 2.1 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Read it offline.
      </Interactive.Div>

      <Interactive.Div
        name="Article card"
        style={{
          position: "absolute",
          top: 620,
          width: 880,
          borderRadius: 56,
          overflow: "hidden",
          background: "white",
          boxShadow: "0 40px 100px rgba(0,0,0,0.5)",
          scale: interpolate(frame, [0, 0.6 * fps], [0.9, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
            output: "perceptual-scale",
          }),
        }}
      >
        <Img
          src={staticFile("articles/lac.jpg")}
          style={{ width: "100%", height: 480, objectFit: "cover", display: "block" }}
        />
        <div style={{ padding: "36px 44px 48px" }}>
          <div style={{ fontFamily: sans, fontWeight: 700, fontSize: 28, color: gold, letterSpacing: 1 }}>
            BOIS DE BOULOGNE
          </div>
          <div style={{ fontFamily: serif, fontWeight: 700, fontSize: 56, lineHeight: 1.12, color: "#111", marginTop: 8 }}>
            Escape the crowds at romantic Lac Inférieur
          </div>
        </div>
        {/* The heart button from the article toolbar */}
        <div
          style={{
            position: "absolute",
            top: 36,
            right: 36,
            width: 120,
            height: 120,
            borderRadius: 60,
            background: "rgba(255,255,255,0.92)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <svg
            viewBox="0 0 24 24"
            width={68}
            height={68}
            style={{
              scale: interpolate(frame, [1 * fps, 1.15 * fps, 1.45 * fps], [1, 1.45, 1], {
                extrapolateLeft: "clamp",
                extrapolateRight: "clamp",
                easing: Easing.bezier(0.34, 1.56, 0.64, 1),
              }),
            }}
          >
            <path
              d="M12 21s-7.5-4.6-10-9.3C.4 8.4 2.3 4.5 6 4.5c2.2 0 3.5 1.2 4.2 2.3h3.6C14.5 5.7 15.8 4.5 18 4.5c3.7 0 5.6 3.9 4 7.2C19.5 16.4 12 21 12 21z"
              transform="translate(0 -1)"
              fill={saved ? "#E0245E" : "none"}
              stroke={saved ? "#E0245E" : "#111"}
              strokeWidth={1.8}
              strokeLinejoin="round"
            />
          </svg>
        </div>
      </Interactive.Div>

      <Interactive.Div
        name="Offline pill"
        style={{
          position: "absolute",
          top: 1560,
          display: "flex",
          alignItems: "center",
          gap: 22,
          padding: "26px 48px",
          borderRadius: 80,
          background: "#1F1C18",
          border: `2px solid ${gold}`,
          fontFamily: sans,
          fontWeight: 600,
          fontSize: 44,
          color: "white",
          opacity: interpolate(frame, [1.9 * fps, 2.4 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [1.9 * fps, 2.6 * fps], ["0px 60px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        <span style={{ fontSize: 52 }}>✈︎</span>
        No signal? No problem.
      </Interactive.Div>
    </AbsoluteFill>
  );
};

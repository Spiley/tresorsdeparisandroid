import {
  AbsoluteFill,
  Easing,
  Interactive,
  interpolate,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import map from "./arrondissements.json";
import { cream, gold, ink, lightGold, sans, serif } from "./theme";

// The same map data as the app: rings and the Seine are flat [x, y, x, y, ...] arrays
const toPath = (points: number[], close: boolean) => {
  let d = "";
  for (let i = 0; i < points.length; i += 2) {
    d += `${i === 0 ? "M" : "L"}${points[i]} ${points[i + 1]}`;
  }
  return close ? d + "Z" : d;
};

const arrondissements = map.arrondissements.map((a) => ({
  number: a.number,
  name: a.name,
  label: a.label,
  d: a.rings.map((ring) => toPath(ring, true)).join(""),
}));
const parks = map.parks.map((p) =>
  p.rings.map((ring) => toPath(ring, true)).join(""),
);
const seine = toPath(map.seine, false);

const highlighted = 4;

export const MapScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const draw = interpolate(frame, [0.2 * fps, 1.8 * fps], [1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.33, 1, 0.68, 1),
  });
  const fill = interpolate(frame, [1.2 * fps, 2 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const select = interpolate(frame, [2.3 * fps, 2.8 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
    easing: Easing.bezier(0.16, 1, 0.3, 1),
  });
  const marais = arrondissements.find((a) => a.number === highlighted)!;

  return (
    <AbsoluteFill
      name="Map"
      style={{ backgroundColor: ink, alignItems: "center" }}
    >
      <Interactive.Div
        name="Headline"
        style={{
          position: "absolute",
          top: 180,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 104,
          lineHeight: 1.05,
          color: "white",
          opacity: interpolate(frame, [0, 0.6 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Explore all 20 arrondissements
      </Interactive.Div>

      {/* Zooms in on the Marais once it is selected, clipped below the headline */}
      <div
        style={{
          position: "absolute",
          top: 470,
          width: 1080,
          height: 900,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            top: 161,
            width: 1080,
            height: 578,
            scale: interpolate(frame, [2.3 * fps, 3.8 * fps], [1, 1.9], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.65, 0, 0.35, 1),
              output: "perceptual-scale",
            }),
            transformOrigin: `${(marais.label[0] / map.width) * 100}% ${(marais.label[1] / map.height) * 100}%`,
          }}
        >
          <svg
            viewBox={`0 0 ${map.width} ${map.height}`}
            width="100%"
            height="100%"
          >
            {arrondissements.map((a) => (
              <path key={a.number} d={a.d} fill={cream} fillOpacity={fill} />
            ))}
            <path d={marais.d} fill={gold} opacity={select} />
            {parks.map((d, i) => (
              <path key={i} d={d} fill="#A8CC98" opacity={fill} />
            ))}
            <path
              d={seine}
              fill="none"
              stroke="#9CC6EA"
              strokeWidth={7}
              strokeLinecap="round"
              strokeLinejoin="round"
              pathLength={1}
              strokeDasharray={1}
              strokeDashoffset={draw}
            />
            {arrondissements.map((a) => (
              <path
                key={a.number}
                d={a.d}
                fill="none"
                stroke={fill > 0.5 ? "white" : lightGold}
                strokeWidth={2}
                strokeLinejoin="round"
                pathLength={1}
                strokeDasharray={1}
                strokeDashoffset={draw}
              />
            ))}
            {arrondissements.map((a) => (
              <text
                key={a.number}
                x={a.label[0]}
                y={a.label[1]}
                textAnchor="middle"
                dominantBaseline="central"
                fontFamily={serif}
                fontWeight={700}
                fontSize={20}
                fill={
                  a.number === highlighted && select > 0.5 ? "white" : "#3B2D14"
                }
                opacity={fill}
              >
                {a.number}
              </text>
            ))}
          </svg>
        </div>
      </div>

      <Interactive.Div
        name="Selected card"
        style={{
          position: "absolute",
          top: 1380,
          width: 860,
          padding: "40px 48px",
          borderRadius: 48,
          background: "white",
          boxShadow: "0 30px 80px rgba(0,0,0,0.4)",
          opacity: interpolate(frame, [2.8 * fps, 3.3 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(
            frame,
            [2.8 * fps, 3.5 * fps],
            ["0px 120px", "0px 0px"],
            {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.16, 1, 0.3, 1),
            },
          ),
        }}
      >
        <div
          style={{
            fontFamily: sans,
            fontWeight: 700,
            fontSize: 30,
            color: gold,
            letterSpacing: 2,
          }}
        >
          4TH ARRONDISSEMENT
        </div>
        <div
          style={{
            fontFamily: serif,
            fontWeight: 800,
            fontSize: 72,
            color: "#111",
            marginTop: 6,
          }}
        >
          {marais.name}
        </div>
        <div
          style={{
            fontFamily: sans,
            fontWeight: 500,
            fontSize: 40,
            color: "#555",
            marginTop: 10,
          }}
        >
          Tap a neighbourhood, find its stories
        </div>
      </Interactive.Div>
    </AbsoluteFill>
  );
};

import { AbsoluteFill, random, useCurrentFrame, useVideoConfig } from "remotion";

const flakes = new Array(70).fill(true).map((_, i) => ({
  x: random(`x-${i}`),
  y: random(`y-${i}`),
  size: 4 + random(`size-${i}`) * 10,
  speed: 1.2 + random(`speed-${i}`) * 2.5,
  drift: random(`drift-${i}`) * Math.PI * 2,
  opacity: 0.35 + random(`opacity-${i}`) * 0.5,
}));

/** Gently falling snow, drawn over the whole video. */
export const Snow: React.FC = () => {
  const frame = useCurrentFrame();
  const { width, height } = useVideoConfig();

  return (
    <AbsoluteFill style={{ pointerEvents: "none" }}>
      {flakes.map((flake, i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: flake.x * width + Math.sin(frame / 30 + flake.drift) * 24,
            top: ((flake.y * (height + 40) + frame * flake.speed) % (height + 40)) - 20,
            width: flake.size,
            height: flake.size,
            borderRadius: flake.size,
            background: "white",
            opacity: flake.opacity,
            filter: "blur(1px)",
          }}
        />
      ))}
    </AbsoluteFill>
  );
};

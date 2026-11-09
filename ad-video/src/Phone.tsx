import { AbsoluteFill } from "remotion";

/** A simple iPhone-like frame; children fill the screen. */
export const Phone: React.FC<{ children: React.ReactNode; style?: React.CSSProperties }> = ({
  children,
  style,
}) => {
  return (
    <div
      style={{
        position: "absolute",
        width: 760,
        height: 1560,
        borderRadius: 110,
        background: "#1a1a1a",
        padding: 22,
        boxShadow: "0 60px 140px rgba(0,0,0,0.55), inset 0 0 0 3px #3a3a3a",
        ...style,
      }}
    >
      <div
        style={{
          position: "relative",
          width: "100%",
          height: "100%",
          borderRadius: 90,
          overflow: "hidden",
          background: "white",
        }}
      >
        <AbsoluteFill>{children}</AbsoluteFill>
        {/* Dynamic Island */}
        <div
          style={{
            position: "absolute",
            top: 22,
            left: "50%",
            translate: "-50% 0",
            width: 200,
            height: 58,
            borderRadius: 30,
            background: "black",
          }}
        />
      </div>
    </div>
  );
};

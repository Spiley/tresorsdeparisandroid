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

export type PhotoSceneProps = {
  image: string;
  /** Width / height of the photo */
  aspect: number;
  /** Horizontal pan across the photo, 0 = left edge, 1 = right edge */
  panFrom: number;
  panTo: number;
  kicker: string;
  title: string;
};

/** A full-screen photo that slowly pans, with a caption at the bottom. */
export const PhotoScene: React.FC<PhotoSceneProps> = ({ image, aspect, panFrom, panTo, kicker, title }) => {
  const frame = useCurrentFrame();
  const { fps, width, height, durationInFrames } = useVideoConfig();
  const overflow = height * 1.08 * aspect - width;

  return (
    <AbsoluteFill name="Photo scene" style={{ backgroundColor: "black", overflow: "hidden" }}>
      <Img
        src={staticFile(image)}
        style={{
          position: "absolute",
          top: -height * 0.04,
          height: height * 1.08,
          width: height * 1.08 * aspect,
          maxWidth: "none",
          left: -overflow * interpolate(frame, [0, durationInFrames], [panFrom, panTo], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      />
      <AbsoluteFill
        style={{ background: "linear-gradient(to bottom, rgba(0,0,0,0.25) 0%, transparent 30%, transparent 45%, rgba(0,0,0,0.7) 70%, rgba(0,0,0,0.9) 100%)" }}
      />
      <Interactive.Div
        name="Kicker"
        style={{
          position: "absolute",
          left: 80,
          right: 80,
          bottom: 470,
          fontFamily: sans,
          fontWeight: 700,
          fontSize: 40,
          letterSpacing: 6,
          color: lightGold,
          textShadow: "0 2px 16px rgba(0,0,0,0.9)",
          opacity: interpolate(frame, [0.15 * fps, 0.5 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        {kicker}
      </Interactive.Div>
      <Interactive.Div
        name="Title"
        style={{
          position: "absolute",
          left: 80,
          right: 80,
          top: 1470,
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 96,
          lineHeight: 1.05,
          color: "white",
          textShadow: "0 4px 30px rgba(0,0,0,0.6)",
          opacity: interpolate(frame, [0.25 * fps, 0.7 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
          translate: interpolate(frame, [0.25 * fps, 0.9 * fps], ["0px 40px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        {title}
      </Interactive.Div>
    </AbsoluteFill>
  );
};

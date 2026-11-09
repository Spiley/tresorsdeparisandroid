import { Audio } from "@remotion/media";
import { linearTiming, springTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { AbsoluteFill, interpolate, staticFile, useVideoConfig } from "remotion";
import { CtaScene } from "./CtaScene";
import { FavoritesScene } from "./FavoritesScene";
import { HookScene } from "./HookScene";
import { MapScene } from "./MapScene";
import { StoriesScene } from "./StoriesScene";

export const Ad: React.FC = () => {
  const { fps, durationInFrames } = useVideoConfig();

  return (
    <AbsoluteFill>
      <TransitionSeries>
        <TransitionSeries.Sequence name="Hook" durationInFrames={75}>
          <HookScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="Stories" durationInFrames={125}>
          <StoriesScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })}
        />
        <TransitionSeries.Sequence name="Map" durationInFrames={135}>
          <MapScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={springTiming({ config: { damping: 200 }, durationInFrames: 15 })}
        />
        <TransitionSeries.Sequence name="Favorites" durationInFrames={105}>
          <FavoritesScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="CTA" durationInFrames={90}>
          <CtaScene />
        </TransitionSeries.Sequence>
      </TransitionSeries>
      {/* "Parisian" by Kevin MacLeod (incompetech.com), CC BY 4.0 – see public/music/CREDITS.md */}
      <Audio
        name="Music"
        src={staticFile("music/parisian-kevin-macleod.mp3")}
        volume={(f) =>
          interpolate(f, [0, 0.5 * fps, durationInFrames - 1.5 * fps, durationInFrames], [0, 0.9, 0.9, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })
        }
      />

      {/* Sound effects (CC0) – see public/sfx/CREDITS.md. Frames are on the full ad's timeline. */}
      <Audio name="Whoosh to Stories" from={58} durationInFrames={8} src={staticFile("sfx/whoosh.wav")} volume={0.7} />
      <Audio name="Feed scroll" from={100} durationInFrames={32} src={staticFile("sfx/scroll_002.ogg")} volume={0.35} />
      <Audio name="Whoosh to Map" from={168} durationInFrames={8} src={staticFile("sfx/whoosh.wav")} volume={0.7} />
      <Audio name="Tap the 4th" from={238} durationInFrames={14} src={staticFile("sfx/mouse-click.wav")} volume={0.8} />
      <Audio name="Card lands" from={255} durationInFrames={8} src={staticFile("sfx/drop_002.ogg")} volume={1} />
      <Audio name="Whoosh to Favorites" from={288} durationInFrames={8} src={staticFile("sfx/whoosh.wav")} volume={0.7} />
      <Audio name="Heart tap" from={318} durationInFrames={14} src={staticFile("sfx/mouse-click.wav")} volume={0.6} />
      <Audio name="Heart pop" from={320} durationInFrames={5} src={staticFile("sfx/pluck_001.ogg")} volume={0.9} />
      <Audio name="Offline pill" from={347} durationInFrames={3} src={staticFile("sfx/select_002.ogg")} volume={1} />
      <Audio name="Download button" from={410} durationInFrames={11} src={staticFile("sfx/confirmation_001.ogg")} volume={0.7} />
    </AbsoluteFill>
  );
};

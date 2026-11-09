import { Audio } from "@remotion/media";
import { linearTiming, springTiming, TransitionSeries } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { AbsoluteFill, interpolate, staticFile, useVideoConfig } from "remotion";
import { PhotoScene } from "./PhotoScene";
import { Snow } from "./Snow";
import { XmasCtaScene } from "./XmasCtaScene";
import { XmasHookScene } from "./XmasHookScene";

// Promo for https://tresors-de-paris.com/christmas-in-paris-2025-what-to-do/
// Dates from the article are left out on purpose.
export const XmasPromo: React.FC = () => {
  const { fps, durationInFrames } = useVideoConfig();

  return (
    <AbsoluteFill style={{ backgroundColor: "black" }}>
      <TransitionSeries>
        <TransitionSeries.Sequence name="Hook" durationInFrames={90}>
          <XmasHookScene />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="Notre-Dame market" durationInFrames={66}>
          <PhotoScene
            image="xmas/notredame-market.jpg"
            aspect={4000 / 1868}
            panFrom={0.38}
            panTo={0.5}
            kicker="10 BEST CHRISTMAS MARKETS"
            title="Cosy stalls next to Notre-Dame"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={springTiming({ config: { damping: 200 }, durationInFrames: 12 })}
        />
        <TransitionSeries.Sequence name="Eiffel Tower market" durationInFrames={66}>
          <PhotoScene
            image="xmas/eiffel-market.jpg"
            aspect={2222 / 1071}
            panFrom={0.42}
            panTo={0.52}
            kicker="10 BEST CHRISTMAS MARKETS"
            title="Treats under the Eiffel Tower"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={springTiming({ config: { damping: 200 }, durationInFrames: 12 })}
        />
        <TransitionSeries.Sequence name="La Villette market" durationInFrames={66}>
          <PhotoScene
            image="xmas/villette.jpg"
            aspect={4000 / 1868}
            panFrom={0.36}
            panTo={0.48}
            kicker="10 BEST CHRISTMAS MARKETS"
            title="A Swiss village with an ice rink"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="Christmas windows" durationInFrames={72}>
          <PhotoScene
            image="xmas/windows.jpg"
            aspect={2252 / 1266}
            panFrom={0.75}
            panTo={0.6}
            kicker="CHRISTMAS WINDOWS"
            title="Dazzling department store displays"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="Nativity scene" durationInFrames={72}>
          <PhotoScene
            image="xmas/nativity.jpg"
            aspect={4000 / 1868}
            panFrom={0.2}
            panTo={0.3}
            kicker="NATIVITY SCENES"
            title="Crèches that will surprise you"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="Provins" durationInFrames={72}>
          <PhotoScene
            image="xmas/provins.webp"
            aspect={3719 / 1737}
            panFrom={0.4}
            panTo={0.52}
            kicker="DAY TRIP"
            title="A medieval Christmas market near Paris"
          />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition presentation={fade()} timing={linearTiming({ durationInFrames: 15 })} />
        <TransitionSeries.Sequence name="CTA" durationInFrames={120}>
          <XmasCtaScene />
        </TransitionSeries.Sequence>
      </TransitionSeries>

      <Snow />

      {/* "Nouvelle Noel" by Kevin MacLeod (incompetech.com), CC BY 4.0 – see public/music/CREDITS.md */}
      <Audio
        name="Music"
        src={staticFile("music/nouvelle-noel-kevin-macleod.mp3")}
        volume={(f) =>
          interpolate(f, [0, 0.3 * fps, durationInFrames - 1.5 * fps, durationInFrames], [0, 0.8, 0.8, 0], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })
        }
      />

      {/* Sound effects (CC0) – see public/sfx/CREDITS.md. Frames are on the full video's timeline. */}
      <Audio name="Title chime" from={14} durationInFrames={5} src={staticFile("sfx/glass_002.ogg")} volume={0.8} />
      <Audio name="Whoosh to Eiffel" from={127} durationInFrames={8} src={staticFile("sfx/whoosh.wav")} volume={0.6} />
      <Audio name="Whoosh to Villette" from={181} durationInFrames={8} src={staticFile("sfx/whoosh.wav")} volume={0.6} />
      <Audio name="Website button" from={429} durationInFrames={11} src={staticFile("sfx/confirmation_001.ogg")} volume={0.45} />
      <Audio name="App row" from={459} durationInFrames={5} src={staticFile("sfx/glass_002.ogg")} volume={0.5} />
    </AbsoluteFill>
  );
};

import { Composition, Folder } from "remotion";
import { Ad } from "./Ad";
import { CtaScene } from "./CtaScene";
import { FavoritesScene } from "./FavoritesScene";
import { HookScene } from "./HookScene";
import { MapScene } from "./MapScene";
import { StoriesScene } from "./StoriesScene";
import { PhotoScene } from "./xmas/PhotoScene";
import { XmasCtaScene } from "./xmas/XmasCtaScene";
import { XmasHookScene } from "./xmas/XmasHookScene";
import { XmasPromo } from "./xmas/XmasPromo";

// Vertical 9:16 for Reels, TikTok and Shorts
export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Folder name="Ad-Scenes">
        <Composition id="Hook" component={HookScene} width={1080} height={1920} fps={30} durationInFrames={75} />
        <Composition id="Stories" component={StoriesScene} width={1080} height={1920} fps={30} durationInFrames={125} />
        <Composition id="Map" component={MapScene} width={1080} height={1920} fps={30} durationInFrames={135} />
        <Composition id="Favorites" component={FavoritesScene} width={1080} height={1920} fps={30} durationInFrames={105} />
        <Composition id="CTA" component={CtaScene} width={1080} height={1920} fps={30} durationInFrames={90} />
      </Folder>
      {/* 75 + 125 + 135 + 105 + 90 minus four 15-frame transitions */}
      <Composition id="TresorsAd" component={Ad} width={1080} height={1920} fps={30} durationInFrames={470} />

      <Folder name="Christmas-Scenes">
        <Composition id="XmasHook" component={XmasHookScene} width={1080} height={1920} fps={30} durationInFrames={90} />
        <Composition
          id="XmasPhoto"
          component={PhotoScene}
          width={1080}
          height={1920}
          fps={30}
          durationInFrames={72}
          defaultProps={{
            image: "xmas/nativity.jpg",
            aspect: 4000 / 1868,
            panFrom: 0.3,
            panTo: 0.42,
            kicker: "NATIVITY SCENES",
            title: "Crèches that will surprise you",
          }}
        />
        <Composition id="XmasCta" component={XmasCtaScene} width={1080} height={1920} fps={30} durationInFrames={120} />
      </Folder>
      {/* 90 + 3 × 66 + 3 × 72 + 120 minus transitions of 15 + 12 + 12 + 4 × 15 frames */}
      <Composition id="XmasPromo" component={XmasPromo} width={1080} height={1920} fps={30} durationInFrames={525} />
    </>
  );
};

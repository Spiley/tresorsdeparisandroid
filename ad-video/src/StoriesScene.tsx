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
import { Phone } from "./Phone";
import { gold, ink, sans, serif } from "./theme";

// Real articles from tresors-de-paris.com
const stories = [
  { image: "articles/seine.jpg", date: "AUGUST 12, 2026", title: "Swimming in the Seine: a free summer experience" },
  { image: "articles/hemingway.jpg", date: "AUGUST 12, 2026", title: "Workshop: “Writing like Hemingway”" },
  { image: "articles/bastille.jpg", date: "JULY 18, 2026", title: "A spring Sunday at the Bastille market" },
  { image: "articles/eclipse.jpg", date: "AUGUST 12, 2026", title: "Live the eclipse: a spectacular evening" },
];

const StoryCard: React.FC<{ image: string; date: string; title: string }> = ({ image, date, title }) => (
  <div style={{ marginTop: 44 }}>
    <Img
      src={staticFile(image)}
      style={{ width: "100%", height: 370, objectFit: "cover", borderRadius: 36, display: "block" }}
    />
    <div style={{ fontFamily: sans, fontWeight: 700, fontSize: 22, color: gold, marginTop: 18, letterSpacing: 1 }}>
      {date}
    </div>
    <div style={{ fontFamily: serif, fontWeight: 700, fontSize: 40, lineHeight: 1.15, color: "#111", marginTop: 6 }}>
      {title}
    </div>
  </div>
);

export const StoriesScene: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  return (
    <AbsoluteFill name="Stories" style={{ backgroundColor: ink, alignItems: "center" }}>
      <Interactive.Div
        name="Headline"
        style={{
          position: "absolute",
          top: 110,
          width: 920,
          textAlign: "center",
          fontFamily: serif,
          fontWeight: 800,
          fontSize: 92,
          lineHeight: 1.05,
          color: "white",
          opacity: interpolate(frame, [0.2 * fps, 0.8 * fps], [0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      >
        Hidden gems & insider stories
      </Interactive.Div>
      <Phone
        style={{
          top: 420,
          translate: interpolate(frame, [0, 0.9 * fps], ["0px 500px", "0px 0px"], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
            easing: Easing.bezier(0.16, 1, 0.3, 1),
          }),
        }}
      >
        <Interactive.Div
          name="Feed"
          style={{
            padding: "120px 36px 0",
            translate: interpolate(frame, [1.4 * fps, 3.6 * fps], ["0px 0px", "0px -900px"], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
              easing: Easing.bezier(0.65, 0, 0.35, 1),
            }),
          }}
        >
          <div style={{ fontFamily: sans, fontWeight: 600, fontSize: 24, letterSpacing: 5, color: gold }}>
            TRÉSORS DE PARIS
          </div>
          <div style={{ fontFamily: serif, fontWeight: 700, fontSize: 52, color: "#111", marginTop: 4 }}>
            Your insider’s guide to Paris
          </div>
          {/* The newest article as a hero card, as on the app's Home tab */}
          <div style={{ position: "relative", marginTop: 36, height: 760, borderRadius: 50, overflow: "hidden" }}>
            <Img
              src={staticFile("articles/lac.jpg")}
              style={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
            <div
              style={{
                position: "absolute",
                inset: 0,
                background: "linear-gradient(to bottom, transparent 35%, rgba(0,0,0,0.85))",
              }}
            />
            <div style={{ position: "absolute", left: 36, right: 36, bottom: 40 }}>
              <div style={{ fontFamily: sans, fontWeight: 700, fontSize: 24, color: gold, letterSpacing: 1 }}>
                LATEST STORY
              </div>
              <div style={{ fontFamily: serif, fontWeight: 700, fontSize: 48, lineHeight: 1.12, color: "white", marginTop: 8 }}>
                Escape the crowds at romantic Lac Inférieur
              </div>
            </div>
          </div>
          {stories.map((story) => (
            <StoryCard key={story.image} {...story} />
          ))}
        </Interactive.Div>
      </Phone>
    </AbsoluteFill>
  );
};

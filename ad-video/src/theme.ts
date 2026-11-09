import { loadFont as loadPlayfair } from "@remotion/google-fonts/PlayfairDisplay";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";

// Serif headlines echo the editorial look of the website and the app
export const serif = loadPlayfair("normal", {
  weights: ["700", "800"],
  subsets: ["latin", "latin-ext"],
}).fontFamily;

export const sans = loadInter("normal", {
  weights: ["500", "600", "700"],
  subsets: ["latin"],
}).fontFamily;

// The app's accent colors (light and dark mode) and its launch-screen black
export const gold = "#B8892B";
export const lightGold = "#D4A94A";
export const ink = "#000000";
export const cream = "#F7EBD3";

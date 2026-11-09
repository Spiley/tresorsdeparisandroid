# App Review – Trésors de Paris (iOS)

Plak het Engelse blok hieronder (tussen de lijnen) zowel als **antwoord in App Store Connect**
als in **App Review Information → Notes**. Het blijft onder de limiet van 4000 tekens.
Voeg de schermopname als bijlage toe aan het antwoord.

---

Hello App Review team,

Thank you for the review. Please find the requested information below. The screen recording (made on a physical iPhone 11 running the latest iOS) is attached.

1. SCREEN RECORDING
Attached. It starts with launching the app from the Home Screen and shows the typical flow: Home feed, opening and reading an article, saving it to Favorites, sharing it, the interactive Paris map, Categories, Search, Favorites (also offline) and the full website view.
The app has NO account registration or login, NO user-generated content and NO paid content or In-App Purchases, so there are no such flows to show.

2. PURPOSE AND TARGET AUDIENCE
Trésors de Paris is the companion app of the Paris city guide tresors-de-paris.com: insider tips on restaurants, hidden gems, museums, events and walks in Paris. It is meant for visitors planning a trip to Paris and for people living in Paris who look for things to do.
Instead of browsing a website on a small screen, the app offers:
- a native article feed and native article reader;
- an interactive map of the 20 arrondissements to find articles by neighbourhood;
- browsing by category and full-text search;
- favorites that are stored on the device with their full text, so saved articles can be read offline while walking around the city (e.g. abroad without mobile data).

3. HOW TO USE THE APP
No login, credentials or sample files are needed. All features are available directly after launch:
- Home: latest articles. Tap an article to read it. In the article, tap the heart to save it to Favorites, or the share button to share the link. The Safari icon at the top right opens the full website.
- Map: tap an arrondissement to see its articles; the list button shows all 20 arrondissements.
- Categories: browse articles by topic.
- Search: type e.g. "museum" and tap Search.
- Favorites: saved articles; swipe left or tap Edit to remove one. Saved articles also open in airplane mode.

4. EXTERNAL SERVICES
- tresors-de-paris.com (our own WordPress website): articles, images and categories are loaded through its public WordPress REST API (https://tresors-de-paris.com/wp-json/wp/v2/). This is the only data source.
- Apple frameworks only: SwiftUI, WebKit (WKWebView) and SafariServices (links to other websites open in Safari View Controller).
No authentication service, payment processor, analytics, tracking SDK or AI service is used. The app collects no personal data; favorites are stored only on the device.

5. REGIONAL DIFFERENCES
None. The app works the same in all regions. The content (in English) is about Paris and is the same for every user.

6. REGULATED INDUSTRY / THIRD-PARTY MATERIAL
The app is not in a regulated industry. All articles and photos come from tresors-de-paris.com. The website is owned and written by my mother, [NAAM MOEDER], who holds the rights to its content. I build and publish this app on her behalf and with her permission; her signed letter of authorization is attached. The arrondissement map is drawn from public geographic boundary data.

Kind regards,
Valentijn

---

## Let op voordat je het verstuurt

- Vul bij **punt 6** de naam van je moeder in op `[NAAM MOEDER]`.
- Voeg de ondertekende verklaring uit [AppReview-Toestemming.md](AppReview-Toestemming.md) als PDF toe aan je antwoord,
  samen met de video.
- Staat de website op jouw naam of op een bedrijf? Pas dan "owned and written by my mother" aan.
- Controleer of de app echt Engelstalig is zoals in punt 5 staat; pas het anders aan.
- Zet bij **App Review Information** het vinkje "Sign-in required" **uit** (er is geen login).

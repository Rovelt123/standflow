# Prompt 1 — Landingpage: Julemarked på Engestofte Gods

Implementér en gennemarbejdet, responsiv og statisk landingpage fra Figma-framen `engestofte-landing-page`. Dette er første af tre opgaver. Implementér kun landingpagen og det beskedne fælles frontendgrundlag, som den kræver. Ansøgningsportal og adminpanel implementeres i de næste prompts.

Den færdige landingpage skal kunne åbnes direkte på `/`.

## Bindende rammer

Du skal udføre implementeringen, ikke blot beskrive en plan. Arbejd i det eksisterende projekt og læs koden, før du ændrer noget.

- Alle filændringer, nye filer, assets, konfigurationer, dependencyændringer og eventuelle billeder skal være inde i `frontend/`.
- Du må læse `systemPromts/Architecture.md` og `systemPromts/Codestandard.md`, men ikke ændre dem eller andre filer i `systemPromts/`.
- Du må ikke ændre `backend/`, repository-roden, database, API, CI/CD, miljøfiler uden for frontend eller andre projekter.
- Denne prompt er gemt i `systemPromts/Frontend/`. Det giver IKKE tilladelse til at skrive implementeringsfiler dér.
- Bevar brugerens eksisterende ændringer. Kontrollér arbejdsområdets tilstand før og efter arbejdet; overskriv ikke uvedkommende arbejde.
- Ingen commits, publicering eller deployment er en del af opgaven.

Læs begge standarddokumenter fuldt ud før implementering. De tekniske regler gælder, men omtalen af “Say I Do”, bryllupsplanlægning og repository-navnet “4.Sem-Sys” er generisk kontekst: det produkt, du bygger her, er julemarkedet på Engestofte Gods fra Figma. Introducér ikke bryllupsindhold.

### Teknologi og kodestandard

1. Brug JavaScript, React, Vite og CSS Modules. Ingen TypeScript-konvertering, Next.js, Tailwind, Bootstrap eller nyt komponentframework.
2. Brug funktionelle React-komponenter med tydeligt ansvar. React-komponenter navngives i PascalCase; funktioner, props og variable i camelCase; mapper i lowercase.
3. Komponentspecifik styling skal ligge i `*.module.css`. Globale styles begrænses til reset, fonte, dokumentgrundlag og fælles designtokens.
4. Overhold dokumentets separator mellem funktioner/metoder, hvor der er flere i samme fil: `//--------------------------------------------------------------`. Opfind ikke hjælpefunktioner blot for at bruge separatoren.
5. Hold indrykning og øvrig formatering ensartet. Ingen ubrugte imports, variable, kommenteret kode, tomme handlers eller døde komponenter.
6. Genbrug eksisterende mønstre og komponenter. Ingen generisk platform, services, repositories, state management-lag eller abstraktioner til hypotetisk fremtidig funktionalitet.
7. Routing skal følge React Router som foreskrevet i Architecture.md. Brug eksisterende routing, hvis den allerede findes; installér kun den nødvendige routerpakke i frontend, hvis den mangler.
8. Ved udarbejdelsen af prompten indeholdt frontend kun React/React DOM og Vite-startskærmen; React Router var ikke installeret. Undersøg den aktuelle kode frem for at antage, at dette stadig gælder.
9. Ved udarbejdelsen brugte `npm run lint` Oxlint, mens Architecture.md kræver ESLint. Hvis forskellen stadig findes, tilføj en lille React/JavaScript-egnet ESLint-konfiguration i frontend og et eksplicit `lint:eslint`-script. Bevar det eksisterende lint-script, og kør begge. Hvis ESLint allerede er konfigureret, genbrug den eksisterende opsætning. Deaktivér ikke regler for at skjule fejl.
10. Ingen drag-and-drop. Arkitekturens omtale af dnd-kit er ikke et krav om at installere eller implementere det i denne statiske opgave.
11. Backend- og API-afsnittene i Architecture.md beskriver det fulde systems ansvar; de er ikke en bestilling på API-kald, login, validering eller forretningslogik her.

## Figma er den visuelle reference

Design: https://www.figma.com/design/pKwmYD6bHAC04UAIBuPHBQ/Julemarked?node-id=0-1&p=f

Linket peger på canvas-siden. Identificér den relevante frame ved dens navn; opfind ikke et specifikt frame-node-id.

De tre observerede frames hedder:
- `engestofte-landing-page`
- `engestofte-ansøgningsportal`
- `engestofte-admin-panel`

Læs den aktuelle frame via tilgængelige Figma-værktøjer eller browser. Se hele fladen, også nederste indhold. Udled sektioner, størrelse, farver, mellemrum, tekster og assets fra designet. De konkrete beskrivelser nedenfor bygger på visuel gennemgang; eksakte fontnavne, farvekoder og lagmål er ikke verificeret via Figma-inspektion.

- Brug faktiske Figma-værdier, hvis de er tilgængelige. Beskriv ellers mål og farver som tilnærmelser, ikke som eksporterede designtokens.
- De observerede desktopframes har omtrent formatet 1280 × 900. Bekræft dette inden en direkte sammenligning; gør ikke hele websiden til en fast 1280-pixelbred flade.
- Genskab layoutet i rigtig HTML/React/CSS. Et screenshot som baggrund for hele siden er ikke en implementering.
- Kopiér ikke Figmas værktøjslinjer, grå canvasbaggrund, frame-navne, cookieknap, musemarkører, “Anonymous”-labels eller blå tilmeldingsbanner ind i produktet.
- Brug dansk tekst, korrekt æ/ø/å og designets stavning. Undgå lorem ipsum og tilfældige engelske labels.
- Opfind ikke ekstra sektioner, modaler, menupunkter, kort eller funktioner. Hvis et element er uklart, inspicér det nærmere.
- Hvis Figma ikke kan læses, gennemfør de uafhængige projektchecks og brug de dokumenterede observationer som grundlag. Bed om de nødvendige eksportfiler/screenshots for en verificerbar gengivelse; hævd aldrig at have kontrolleret noget, du ikke kan se.
- Manglende originale assets eller fonte skal fremgå af slutrapporten. En eventuel erstatning skal være tydeligt beskrevet som en tilnærmelse.

## Absolut krav: kun statisk frontend

Siden skal ligne designet, men ingen af dens kontroller skal udføre handlinger.

- Ingen backend, API-klient, fetch, Axios, WebSocket, serverfunktioner, database, mockserver, betaling, mail eller autentifikation.
- Ingen localStorage, sessionStorage, cookies, caching af brugerdata eller anden persistens.
- Ingen formularvalidering, beregninger, søgning, filtrering, sortering, paginering, eksport, upload, booking eller godkendelsesflow.
- Ingen `onClick`, `onSubmit` eller `onChange` til produktfunktioner. Ingen tomme callbacks, console.log, alert, toast eller simuleret succesbesked.
- Ingen useState/useReducer/useEffect til at få mockup-kontroller til at virke. Statiske arrays og direkte rendering med stabile keys er tilladt.
- Alle knapper skal være `type="button"`, uden handlers og uden implicit submit.
- Linklignende navigation, logo, sociale ikoner og call-to-actions skal også være inaktive. Brug ikke `href="#"`, eksterne links eller React Router-links, der navigerer ved klik.
- Inaktive handlinger skal formidles til hjælpeteknologi med passende semantik, eksempelvis `aria-disabled="true"` på en knap uden handler. Bevar designets synlige farver; undgå at browserens standard-disabled-styling gør alt gråt.
- `aria-disabled` alene forhindrer ikke en native select, checkbox eller anden kontrol i at ændre sig. Brug readOnly til tekstfelter og disabled eller en korrekt beskrevet statisk repræsentation til kontroller uden readOnly-understøttelse.
- Undgå aktive form-tags, submitknapper, details/summary, filinput og andre native standardhandlinger, som kan skabe funktionalitet uden en handler.
- Felter viser faste eksempelværdier. Brug `readOnly` og tilknyttede labels på egentlige tekstfelter; ikke aktiv dataindtastning.
- Almindelig dokumentscrolling, browserzoom, CSS-breakpoints og diskrete CSS-fokus/hover-udtryk er tilladt. Ingen åbning af menuer, accordions, faneskift eller ændring af værdier.
- Den eneste nødvendige applikationsinfrastruktur er at rendere en statisk side på en direkte URL via React Router. Manuel indtastning af en URL må vise siden; produktets knapper må stadig ikke navigere.
- Fonts og billeder må indlæses som statiske assets. Brug helst lokale filer i frontend frem for eksterne runtime-afhængigheder.

## Fælles visuelt og teknisk grundlag

Udtrykket er klassisk herregårdsjul: dyb bordeaux, mørkere vinrøde flader, varm creme, afdæmpet guld, fine kanter og elegant seriftypografi. Genskab referencen frem for at redesigne den til et moderne standarddashboard.

- Brug CSS custom properties til fælles farver, fontfamilier, afstande, kanter og radier, afledt af designet.
- Undgå store skygger, meget runde “app-kort”, gradients og animationer, som ikke fremgår af designet.
- Bevar tekstens hierarki: stor sideoverskrift, mindre introduktion, små sektionslabels og læsbar detaljetekst.
- Match både overordnet geometri og indvendig spacing: containerbredde, gutters, linjehøjde, padding, ikonstørrelse, border og billedbeskæring.
- Genbrug et offentligt headerkomponent mellem landingpage og ansøgningsportal, hvor designet er fælles. Brug props/varianter til forskelle i aktiv markering og CTA.
- Adminheaderen er særskilt og skal ikke automatisk arve den offentlige navigation.
- Giv informative billeder passende alt-tekst og dekorative ikoner tom alt/aria-hidden. Ikonknapper skal have forståelige labels.
- Brug semantiske landmarks og en logisk overskriftstruktur. Fokus og tekst må ikke blive usynlige.
- Ingen dynamisk tilføjelse af klasser baseret på klik. Aktive/valgte tilstande fastlægges statisk fra Figma.
- Brug CSS Grid/Flexbox til struktur. Absolute positioning er kun til eksempelvis billedoverlays og små dekorationer.
- Bevar normal dokumenthøjde; skjul ikke fejl eller manglende indhold med en global `overflow: hidden`.
- Ingen mobilframe blev verificeret ved udarbejdelsen. Hvis designet indeholder mobilframes, brug dem. Ellers tilpas det eksisterende visuelle hierarki med CSS og angiv mobiltilpasningen som en fortolkning.

## Landingpage: konkret design og indhold

### 1. Header

- Fuld bredde med bordeaux baggrund og en lav, vandret desktopopbygning.
- Til venstre ses Engestofte Gods-logoet i en fin guldfarvet, rektangulær ramme.
- Midterområdet har punkterne “Om godset”, “Julemarked”, “Stadeholdere” og “Kontakt”.
- Til højre ses Instagram- og Facebook-ikoner samt en smal, afrundet outline-knap med “ANSØG OM EN STAND”.
- Match logoets proportioner og den luft, der er mellem venstre logo, navigation og højre handlinger.
- Headerens visuelle højde er omkring 70 pixels i den observerede desktopkomposition; mål og korrigér efter Figma.
- Alle navigationselementer, sociale ikoner og logoet er inaktive. Headeren må ikke blive sticky, medmindre det kan verificeres i designet.
- Brug den egentlige logoasset, hvis tilgængelig. En tekstlig erstatning skal oplyses som en afvigelse.

### 2. Hero

Heroen fylder hele bredden under headeren og omtrent halvdelen af desktopframens højde. Billedet er en vinterlig herregårdsgård ved aften med bindingsværksbygninger, varme lyskæder, sne og et stort oplyst juletræ.

- Brug originalbilledet, hvis det kan eksporteres eller findes i projektets assets. Vite-startskærmens hero.png er ikke automatisk det rigtige billede.
- Bevar billedets fokuspunkt og den rolige komposition omkring teksten.
- Læg en mørk bordeauxtonet gradient over billedet, kraftigere nederst, så det flyder ind i resten af siden.
- Tekstblokken er venstrestillet, rykket ind med samme venstre gutter som de næste sektioner og placeret i heroens nederste del.
- Lille datoetiket med tynd guldkant; den observerede tekst er “5.–6. DECEMBER 2026”. Genbekræft tegnsætning direkte i designet.
- Stor lys serifoverskrift: “Julemarked på Engestofte Gods”.
- Introduktionens observerede tekst:

  “Træd ind i en fortryllende verden af ægte herregårdsjul. Over to dage, fredag–lørdag kl. 10:00–16:00, fyldes godsets historiske lader og gårdsplads med duften af brændte mandler, gran og lune æbleskiver. Oplev over 80 unikke boder med dansk kunsthåndværk, lokale råvarer og smuk julepynt ved bredden af Maribo Søndersø.”

- Kopiér den aktuelle Figma-tekst ved afvigelser; ret ikke datoer eller ugedage ud fra egne antagelser.
- Introduktionen har begrænset linjelængde og mindre skrift end overskriften. Match linjeskift via containerbredde frem for mange hårdkodede br-tags.
- Under teksten: primær guldfyldt knap “ANSØG OM EN STAND” og sekundær transparent outline-knap “SE PLANTEGNING”.
- Begge knapper er visuelt komplette og uden handling. Ingen scroll til sektion, navigation, booking eller åbning af plantegning.
- Intet carousel, parallax, sneanimation eller video.

### 3. Tre trin

Under heroen ligger et mørkere bordeauxbånd med tre ligeværdige kolonner og en diskret nederste kant.

Hver kolonne består af et lille cirkulært guldoutline-ikon, lille trinlabel, serifoverskrift og kompakt brødtekst:

1. “TRIN 1” / “Ansøg online”: ansøgningsformular med produkter, billeder af stand og behov for strøm/inventar.
2. “TRIN 2” / “Bliv godkendt”: juleudvalgets løbende vurdering af ansøgninger og udvælgelse til godsets rammer.
3. “TRIN 3” / “Book din plads”: efter godkendelse omtales link til booking, betaling og valg af stand på plantegningen.

Det er forklarende indhold. Omtalen af booking, betaling og ansøgning er ikke en bestilling på funktionalitet.

- Kopiér brødteksten fra Figma så præcist som muligt; ovenstående beskrivelser er indholdsresuméer.
- Ikonerne fremstår som henholdsvis clipboard/check, medalje og bod/butik.
- Match ikonernes stregtykkelse, cirkelstørrelse og afstand til tekst.
- Brug en statisk liste/array, hvis det giver enklere kode; ingen kliktilstand eller progression.

### 4. Tre billedkort

Nederst ses tre vandrette kort på bordeaux baggrund, med ens bredde, rolige mellemrum og let afrundede hjørner.

- Første kort: stemningsbillede af boder og kunsthåndværk, lille label “80+ UNIKKE BODER”, titel “Dansk Kunsthåndværk”.
- Andet kort: mad, drikke og julestemning, lille label “LOKALE RÅVARER”, titel “Smag Julen”.
- Tredje kort: godset ved vandet om aftenen, lille label “VED MARIBO SØNDERSØ”, titel “Ægte Herregårdsjul”.
- Kontrollér den aktuelle Figma-stavning, kapitalisering og små labels.
- Tekst ligger nederst i billedet over et mørkt overlay. Match udsnit, kontrast og højde.
- Hele kortet er statisk; ingen links, zoom eller modal.
- Tilføj ikke footer, newsletter, testimonials eller ekstra sektioner. Den observerede frame slutter efter billedkortene.

## Landingpage: komponenter og responsivitet

Mulig struktur, tilpasset eksisterende projekt:

- `src/pages/landing/LandingPage.jsx` og tilhørende CSS Module.
- `src/components/layout/PublicHeader.jsx` og CSS Module.
- `src/components/ui/Button.jsx`, hvis de reelt gentagne visuelle knapper begrunder den.
- Sidekomponenter til hero, procestrin og oplevelseskort i lowercase-mapper, hvis det forbedrer overskueligheden.
- `src/styles/tokens.css` til fælles verificerede/tilnærmede tokens.
- Lokale billeder, logoer og fonte under `src/assets/` eller `public/`.

Opret ikke alle foreslåede filer mekanisk. Hold arkitekturen lille og let at læse.

- Fjern Vites demoindhold, tæller, React/Vite-logoer og demostyling fra den aktive landingpage.
- Etablér kun ruten `/` i denne opgave. Opret ikke tomme portalsider eller adminfunktioner.
- På smalle skærme: lad headerens statiske indhold tilpasse sig med wrap/kolonner, og lad knapperne stables efter behov. Opfind ikke en fungerende hamburgermenu.
- Procestrin og billedkort går fra tre kolonner til færre/en kolonne, når pladsen kræver det.
- Heroen må vokse i højden med teksten. Bevar juletræets og bygningernes motiv så godt som muligt med responsiv object-position/background-position.
- Tilpas overskriftens størrelse uden at gøre brødtekst ulæselig.

## Landingpage: specifikke acceptkriterier

- [ ] Header, hero, tre procestrin og tre billedkort er implementeret i korrekt rækkefølge.
- [ ] Ingen ekstra sektioner og intet tilbageværende Vite-demoindhold.
- [ ] Billedvalg, mørke overlays, serifhierarki og bordeaux/guld-farver matcher referencen så tæt som de tilgængelige assets tillader.
- [ ] Begge hero-knapper, headernavigation, sociale ikoner og kort er inaktive.
- [ ] Fælles header/tokens kan genbruges i prompt 2 uden et nyt designsystem.
- [ ] Ingen ansøgningsportal eller adminpanel er implementeret i denne opgave.

## Kontrol

Udfør kontrollen, før opgaven erklæres færdig:

1. Kør `npm run build` og `npm run lint` fra frontend.
2. Åbn den direkte sideroute i browseren, og kontrollér, at genindlæsning viser samme statiske side uden krav om backend.
3. Sammenlign med den relevante Figma-frame ved tilsvarende viewport. Kontrollér først sektioner og proportioner, derefter typografi, farver, assets, spacing og detaljer.
4. Kontrollér mindst desktop 1280 × 900 og 1440 × 900, tablet omkring 768 pixels og mobil omkring 390 og 320 pixels i bredden. Der må ikke være utilsigtet vandret scrolling på hele dokumentet.
5. Kontrollér hele siden, ikke kun første viewport. Ingen skjulte felter, afskåret tekst, strakte billeder, overlap eller manglende ikoner.
6. Prøv sidens synlige handlinger med mus og tastatur. Klik og Enter må ikke navigere, sende, ændre data eller åbne noget. Native kontroller må heller ikke ændre værdier.
7. Kontrollér konsollen for React-fejl, manglende keys, asset-fejl og fejl om kontrollerede felter.
8. Kontrollér at der ikke er introduceret API-kald, persistens, eventhandlers eller funktionel state til mockuppen. Statiske assetforespørgsler og Vites udviklingsforbindelse er ikke backendintegration.
9. Gennemgå ændrede filer og bekræft, at alle ændringer fra denne opgave ligger under frontend. Undlad at tilbageføre allerede eksisterende ændringer uden for dit arbejde.
10. Ret fejl inden aflevering. Tilføj ikke en stor testsuite eller nye testafhængigheder til denne rent visuelle opgave; brug de relevante eksisterende checks og browserkontrol.
11. Hvis et check ikke kan køres, angiv den præcise årsag. Skriv ikke “testet” eller “pixelperfekt” uden grundlag.



Start med at læse standarderne og den eksisterende frontend, og gennemfør derefter implementeringen inden for denne prompts sideafgrænsning.
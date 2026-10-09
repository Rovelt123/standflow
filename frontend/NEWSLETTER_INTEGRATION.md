# Email og nyhedsbreve – frontendintegration

## Funktionalitet

- Registrering har et valgfrit, som udgangspunkt umarkeret marketingsamtykke.
- Profilindstillinger henter samtykke fra serveren og viser kun bekræftede ændringer. Checkboxen skjules, mens den gemte værdi er ukendt.
- Adminmenuens indstillinger åbner samme profilindstillinger. Profil og beskeder kræver ikke længere samtidig indlæsning af kundens ansøgninger; det sikrer også adgang til samtykke for administratorer uden USER-rollen.
- `/unsubscribe?token=...` er offentlig og kræver et klik på bekræftelsesknappen. Token vises eller logges ikke.
- `/kontrolpanel/nyhedsbrev` bruger eksisterende adminbeskyttelse og rummer skabelonliste, oprettelse, redigering, sletning med bekræftelse, validering og forhåndsvisning.
- Skabeloner er almindelig tekst. Forhåndsvisning bruger serverens gemte skabelon med eksempelmodtageren Ola Nordmann; ændringer skal gemmes først.
- Udsendelse understøtter alle seks modtagergrupper og standtype A–H. Modtagere kan vælges fra eksisterende samtaler eller angives med kendte bruger-id'er.
- Før udsendelse viser en bekræftelsesdialog skabelon, modtagergruppe og advarsel om rigtige mails. Aktive anmodninger blokerer gentagen indsendelse.
- Resultatet viser serverens faktiske antal sendt, sprunget over og fejlet. Udsendelser genforsøges aldrig automatisk.
- `/messages` fører til kundens eller administratorens eksisterende beskeder. Efter login fra dette link bevares destinationen.
- Ansøgningsindsendelse og chatbeskeder bruger fortsat de eksisterende kald uden yderligere mailanmodninger.

## API-kontrakter

Alle stier nedenfor har præfikset `/api`. Eksisterende API-baseadresse og Bearer-autentificering genbruges. Offentlig afmelding sender ingen Bearer-header.

| Metode og sti | Anvendelse og faktisk svar |
| --- | --- |
| `POST /users/auth/register` | Eksisterende registrering med eksplicit boolean `acceptMarketing` |
| `GET /users/me` | Eksisterende bruger-/rolleafklaring og beskeddestination |
| `GET /users/me/consent` | Hent `{ marketingConsent }` |
| `PATCH /users/me/consent` | Gem `{ marketingConsent }`; vis serverens svar |
| `POST /users/me/unsubscribe` | Afmeld den indloggede bruger |
| `POST /newsletter/unsubscribe` | Send `{ token }`; bekræft `{ marketingConsent: false }` |
| `GET /template` | Skabeloner ligger i `data.data`, også ved tom liste |
| `GET /template/{id}` | Skabelonen ligger i `data.data` |
| `POST /template` | Send kun `name`, `subject`, `body`; direkte skabelon-DTO, HTTP 201 |
| `PUT /template/{id}` | Samme felter; direkte skabelon-DTO, HTTP 200 |
| `DELETE /template/{id}` | HTTP 204 uden JSON |
| `POST /template/validate` | Send kun `subject`, `body`; svar med `valid`, `unknownVariables` |
| `POST /template/{id}/render` | Send `firstName`, `lastName`, `company`, `email`; svar med `subject`, `body`, `unknownVariables` |
| `GET /messages/threads?sort=name` | Eksisterende samtalers `customerId`, `customerName`, `company` til modtagervalg |
| `POST /newsletter` | HTTP 202 med færdige tællere: `sentTo`, `skippedNoConsent`, `failedToSend` |

`INDIVIDUAL` sender `recipientIds`; `CATEGORY` sender `category` (A–H). De resterende grupper sender ingen ekstra modtagerfelter: `NEW_STALLHOLDERS`, `PREVIOUS_YEAR_STALLHOLDERS`, `ALL_PREVIOUS_STALLHOLDERS`, `ALL_APPLICANTS`.

## Backendbegrænsninger og afvigelser fra opgaveeksempler

- Der findes ingen generel brugersøgnings- eller brugerliste-API. Ansøgnings-DTO'er indeholder ikke bruger-id. Modtagervælgeren viser derfor kun eksisterende samtalepartnere; andre modtagere kræver et kendt bruger-id. Ingen nye endpoints er opfundet.
- `ALL_APPLICANTS` er brugere med ansøgninger, ikke alle registrerede brugere.
- Valideringsendpointet kontrollerer ukendte pladsholdere. Navn, emne og tekst valideres ved lagring; formularens længdegrænser følger backendens 150/200/5000 tegn.
- Udsendelse afsluttes på serveren før svaret, selv om statuskoden er 202. UI'et viser derfor de returnerede leveringstal frem for at hævde, at en baggrundsopgave er startet.
- Ugyldige, udløbne og allerede brugte afmeldingstokens får samme HTTP 400 fra backend. UI'et viser denne fælles fejl og henviser til det seneste nyhedsbrev eller profilindstillinger.
- En afbrudt udsendelse kan allerede have sendt nogle mails. UI'et advarer om dette og genforsøger ikke automatisk.

## Verifikation

Kørt fra `frontend/`:

- `node --test tests/*.test.js`: 39 tests bestået. Omfatter nye kontrakttests og eksisterende registrerings-, samtykke- og ansøgningstests. Alle API-svar er simulerede.
- `npm run build`: bestået.
- `npm run lint:eslint`: bestået uden fejl.
- `npm run lint`: bestået med fem eksisterende advarsler om state i effects i øvrige sider. Ingen advarsler i de ændrede nyhedsbrevskomponenter.
- `git diff --check`: bestået.
- Ingen backendtests eller rigtige mailudsendelser er kørt.

Sandboxen blokerede først Node/Vite-underprocesser med EPERM. Build og tests blev kørt med tilladt eskalering. Et indledende forsøg uden testisolering gav konflikter mellem testfilernes globale storage-mocks; den normale isolerede testkørsel ovenfor bestod.

Browserforløbet i `tests/newsletter-ui.html` og `tests/newsletter-ui.jsx` simulerer alle API-kald og kontrollerer formularer, samtykke, afmelding, skabeloner, bekræftelser, dobbelte indsendelser, leveringsfejl og rollebaserede beskedlinks. Det kan køres med:

```text
npm run dev -- --host 127.0.0.1 --port 5178
http://127.0.0.1:5178/tests/newsletter-ui.html
```

Browserforløbet er **ikke afviklet** her, fordi ingen browser var tilgængelig. Vite kunne transformere og levere testmodulet med HTTP 200. Visuel og interaktiv browserkontrol mangler derfor fortsat.

## Lokal konfiguration

Brug den eksisterende backend og frontendopsætning. Frontend anvender `VITE_API_BASE_URL`, hvis den allerede er sat; ellers `http://localhost:9393/api` under udvikling og `/api` i produktion. Der er ingen nye frontendmiljøvariabler eller afhængigheder.

SMTP-oplysninger hører kun til backend og er ikke kopieret, brugt eller gemt i frontend. Backendens eksisterende `FRONTEND_URL` skal pege på frontend, og hosting skal kunne levere appen ved direkte besøg på `/unsubscribe` og `/messages`. Serveropsætningen er ikke ændret eller verificeret med rigtige mails.

## Filer

Ændret:

- `src/App.jsx`
- `src/components/admin/AdminSidebar.jsx`
- `src/pages/public/AccessPage.jsx`
- `src/pages/public/UnsubscribePage.jsx`
- `src/pages/public/newsletterApi.js`
- `src/pages/portal/ProfileSettings.jsx`
- `src/pages/portal/PortalPage.jsx`
- `src/pages/portal/portalApi.js` (rettet forældet kommentar)
- `src/pages/kontrolpanel/NewsletterPage.jsx`
- `src/pages/kontrolpanel/NewsletterPage.module.css`
- `tests/authApi.test.js`

Oprettet:

- `src/pages/public/MessagesRedirect.jsx`
- `src/pages/kontrolpanel/TemplateManager.jsx`
- `src/pages/kontrolpanel/NewsletterDelivery.jsx`
- `src/pages/kontrolpanel/newsletterAdminApi.js`
- `tests/newsletterApi.test.js`
- `tests/newsletter-ui.html`
- `tests/newsletter-ui.jsx`
- `NEWSLETTER_INTEGRATION.md`

Alle ændringer ligger i frontend. Backend, afhængighedsfiler, fælles konfiguration og lockfiler er uændrede. Intet er committed eller pushed.

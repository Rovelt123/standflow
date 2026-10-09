# Verifikationsrapport: frontend og backend

Dato: 9. oktober 2026.

**Konklusion:** Frontend kører, begge projekter kan bygges, og de eksisterende automatiske tests består. Reel integration med den normalt startede backend er **ikke verificeret**: backend stopper under databaseinitialisering, fordi PostgreSQL på `localhost:5432` ikke er tilgængelig. Docker Desktop kunne ikke fuldføre opstart eller genstart. Ingen browser var tilgængelig.

Brugeren har bekræftet, at `localhost:5432/standflow` er en lokal testdatabase, og godkendt midlertidige testbrugere/skabeloner. Ingen sådanne data blev oprettet, fordi databasen ikke kunne nås.

## Status og evidens

| Område | Status | Observeret resultat |
| --- | --- | --- |
| Frontendopstart | **Verificeret** | Vite startet på `http://127.0.0.1:5178/`; HTTP 200 |
| Frontendens direkte ruter | **Verificeret, kun HTTP** | `/registrer`, `/brugerportal?tab=settings`, `/unsubscribe`, `/kontrolpanel/nyhedsbrev`, `/messages` leverer HTML med HTTP 200. Dette beviser ikke rendering, adgangskontrol eller formularfunktion |
| Frontendens API-baseadresse | **Verificeret, konfiguration** | Det serverede Vite-modul har `DEV: true`, ingen `VITE_API_BASE_URL`-override og standard `http://localhost:9393/api` |
| Backendbuild | **Verificeret** | Maven package gennemført; `backend/target/backend.jar` bygget |
| Backendopstart | **Fejlet** | `java -jar target/backend.jar` afsluttede med exit 1, `ExceptionInInitializerError`, årsag `java.net.ConnectException: Connection refused: getsockopt` |
| PostgreSQL-forbindelse | **Fejlet** | Ingen forbindelse på `127.0.0.1:5432`; backend opnåede ingen databaseforbindelse. Database-login og skema kunne derfor ikke undersøges |
| Docker/WSL | **Fejlet** | Docker Desktop blev stående i `starting`; WSL-listen viste `docker-desktop` som `Stopped`. Containerforespørgsel gav Docker API HTTP 500. Én genstart med 45 sekunders timeout fejlede med `Docker Desktop is still starting: context deadline exceeded` |
| API til kørende backend | **Ikke testet / blokeret** | GET `http://localhost:9393/api/users/me/consent` kunne ikke etablere HTTP-forbindelse; ingen backendstatuskode modtaget |
| Frontendens forbindelsesfejl | **Verificeret, negativ kontrol** | Den faktiske `unsubscribeFromNewsletter`-hjælper blev kørt fra Node med rigtig HTTP-fetch til localhost og et ugyldigt testtoken. Den viste `Forbindelsen blev afbrudt. Prøv igen senere.` Ingen request nåede en kørende backend |
| Frontendtests | **Verificeret, simulerede API-svar** | 39 tests, 0 fejl |
| Backendtests | **Verificeret, H2/testmiljø** | 103 tests, 0 failures, 0 errors, 0 skipped. Testservere og H2 er ikke den normalt startede PostgreSQL-backend; mailtransport er simuleret |
| Frontendbuild | **Verificeret** | `npm run build` bestået |
| ESLint | **Verificeret** | `npm run lint:eslint` bestået |
| Oxlint | **Verificeret med advarsler** | `npm run lint` bestået; fem eksisterende `set-state-in-effect`-advarsler i ExhibitorsPage, StadeholderePage, KontrolpanelPage og MessagesPage (to) |
| Browsertest | **Ikke testet** | Browserinventaret var tomt: ingen apps/browsere tilgængelige |

## Featurestatus mod faktisk backend

Ingen af nedenstående funktioner er testet med succes mod den normalt startede backend i denne kørsel.

| Funktion | Status | Hvad mangler |
| --- | --- | --- |
| Registrering med/uden marketingsamtykke | **Ikke testet** | PostgreSQL/backend og to midlertidige testkonti |
| Hent, tilmeld og afmeld samtykke | **Ikke testet** | Autentificeret testkonto og faktisk serverbekræftelse efter genindlæsning |
| Offentlig afmelding | **Ikke testet** | Kørende backend, gyldigt lokalt testtoken samt ugyldigt/udløbet/brugt token. Kun forbindelsesfejlen er afprøvet |
| Adminskabelon: opret, rediger, slet | **Ikke testet** | Lokal testadmin og en ny midlertidig skabelon |
| Skabelonvalidering | **Ikke testet** | Faktisk svar fra `/template/validate` |
| Forhåndsvisning og variabler | **Ikke testet** | Faktisk svar fra `/template/{id}/render` |
| Individuelt modtagervalg | **Ikke testet** | Faktisk samtaleliste og verificering af de valgte `customerId`-værdier |
| Segmentering | **Ikke testet** | Backendens reelle udvælgelse er ikke afprøvet. Ingen dry-run/modtagerpreview-API findes; valg i UI alene beviser ikke segmenteringen |
| Bekræftelse før udsendelse | **Ikke testet** | Browserkontrol, hvor dialogen åbnes og annulleres |
| Leveringstal | **Ikke testet** | Ingen rigtig udsendelsesanmodning udført. Mock-/H2-tests beviser ikke SMTP-levering |
| Rollebaseret `/messages` | **Ikke testet** | Browser med almindelig bruger, admin og udlogget session |

## Startkommandoer og eksisterende konfiguration

Kør kommandoerne i PowerShell fra projektroden. Java og Maven findes lokalt, men var ikke på PATH. Ingen permanente miljøvariabler er ændret.

Backendbuild med eksisterende testopsætning:

```powershell
$env:JAVA_HOME = 'C:\Users\Olive\.jdks\azul-21.0.12.1'
Set-Location backend
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' package
```

Når den eksisterende PostgreSQL-testdatabase svarer på port 5432, start backend fra `backend/`:

```powershell
& 'C:\Users\Olive\.jdks\azul-21.0.12.1\bin\java.exe' -jar target/backend.jar
```

Start frontend fra `frontend/` i en anden terminal:

```powershell
npm run dev -- --host 127.0.0.1 --port 5178 --strictPort
```

Frontendserveren fra verifikationen er efterladt kørende på port 5178. Start ikke endnu en instans på samme port. Backendprocessen er afsluttet efter fejlen. Docker nåede ikke en bekræftet kørende tilstand.

Konfiguration læst uden at udskrive hemmeligheder:

- Backend: `DEPLOYED=false`, `PORT=9393`, `DB_NAME=standflow`. Udviklingsgrenen i `HibernateConfig` bruger `jdbc:postgresql://localhost:5432/standflow`; værdien af `CONNECTION_STR` anvendes kun i deployed-grenen.
- Hibernate bruger `hbm2ddl.auto=update`. Start derfor kun mod den bekræftede lokale testdatabase.
- Frontend: eksisterende Vite-opsætning, ingen `.env*` i frontend og ingen `VITE_API_BASE_URL` i den aktuelle shell.
- Backendens `FRONTEND_URL` peger fortsat på `https://standflow.roneu.dk`. Lokalt genererede maillinks ville derfor pege på den host. Dette er en konfigurationsobservation, ikke en afprøvet maillevering.
- Ingen `backend/.env` eller SMTP-miljøvariabler blev fundet i den aktuelle CLI-kontekst. En eventuel særskilt IntelliJ-run-konfiguration er ikke verificeret. Oplysninger fra chatten er ikke kopieret eller anvendt.
- Backendens CORS-kode tillader alle origins; faktisk CORS-respons og browserhåndhævelse er ikke testet, fordi serveren ikke startede.

## Fejl og anbefalede næste skridt

1. **Løs Docker-opstarten først.** Åbn Docker Desktop og undersøg engine/WSL-fejlen. Loggen viste gentagne `/ping`-timeouts mod den interne engine. Én almindelig CLI-genstart blev forsøgt uden succes. Ingen reset, geninstallation, sletning af volumes eller afslutning af andre WSL-distributioner er udført.
2. **Start den eksisterende lokale PostgreSQL-container.** Containerlisten kunne ikke læses, så rapporten opfinder ikke et containernavn. Kontrollér i Docker Desktop eller med `docker ps -a`, at den eksisterende database publicerer port 5432. Kontrollér derefter `Test-NetConnection localhost -Port 5432` og start backend igen.
3. **Kontrollér de nedenstående browserforløb mod localhost.** Brug nye testkonti/skabeloner og ryd kun disse op. Undlad at anvende produktionssitet til testdata.
4. **Afklar lokal maillink-konfiguration før godkendte mailtests.** Backendens eksisterende `FRONTEND_URL` skal passe til den ønskede frontend, hvis maillinks skal bruges lokalt. Ingen backendkonfiguration er ændret her. SMTP skal håndteres privat via eksisterende konfiguration.
5. **Ingen backendkodeændring anbefales på det nuværende grundlag.** Der er påvist et lokalt opstartsproblem, ikke en bekræftet fejl i frontend/backend-kontrakten. Eventuelle backendrettelser kræver særskilt godkendelse efter reproduktion.

Det første Maven-forsøg kørte offline: alle 103 tests bestod, men pakningen fejlede, fordi flere afhængigheder til shade-pluginet ikke var cachet. Et efterfølgende online `mvn -DskipTests package` hentede de manglende afhængigheder og gav `BUILD SUCCESS`. Dette buildproblem er løst uden ændringer i `pom.xml`.

## Manuelle integrationstrin

Forudsætning: backend kører på `http://localhost:9393`, PostgreSQL-forbindelsen er bekræftet, og browseren er åbnet på `http://127.0.0.1:5178`. Åbn browserens Network-panel og kontrollér, at requests går til **localhost:9393**, ikke produktionshosten. Test aldrig via `tests/newsletter-ui.html`, når formålet er reel integration: den side erstatter alle API-kald med mocks.

### 1. Registrering

URL: `http://127.0.0.1:5178/registrer`

- Opret en ny lokal testkonto med alle krævede felter, ottecifret CVR, ens adgangskoder og obligatoriske accepter. Lad nyhedsbrevskrydset være tomt.
- Kontrollér, at checkboxen oprindeligt var umarkeret, og at `POST /api/users/auth/register` sender `acceptMarketing: false` og svarer HTTP 201.
- Log ud, og gentag med en anden ny testkonto og markeret checkbox. Kontrollér `acceptMarketing: true` og HTTP 201.
- Brug ikke eksisterende konti som testdata. Del ikke passwords eller auth-tokens fra Network-panelet.

### 2. Samtykke

URL: `http://127.0.0.1:5178/brugerportal?tab=settings`

- Log ind med hver testkonto. Kontrollér `GET /api/users/me/consent` og den viste værdi.
- Slå til/fra og kontrollér PATCH-payload, HTTP 200 og `marketingConsent` i serverens svar. Genindlæs siden for at kontrollere lagring.
- Brug knappen “Afmeld marketing”; kontrollér `POST /api/users/me/unsubscribe` og `marketingConsent: false`.
- Gentag ved midlertidigt at vælge Offline i browserens Network-panel. UI'et skal vise fejl og beholde senest bekræftede værdi. Slå Offline fra bagefter.

### 3. Offentlig afmelding

URL uden token: `http://127.0.0.1:5178/unsubscribe`

URL med ugyldigt testtoken: `http://127.0.0.1:5178/unsubscribe?token=invalid-local-test-token`

- Log ud. Uden token skal siden vise manglende link og ikke sende POST.
- Med ugyldigt token skal åbning alene ikke sende POST. Klik “Ja, afmeld mig”; kontrollér HTTP 400 og fejlbesked.
- Et gyldigt forløb kræver et **allerede eksisterende eller særskilt klargjort token til en lokal testkonto**. Åbn `http://127.0.0.1:5178/unsubscribe?token=<lokalt-testtoken>`, bekræft og forvent HTTP 200 med `marketingConsent: false`. Tokenværdien må ikke kopieres ind i rapporter/logs.
- Åbn samme link igen efter succes og bekræft: backend skal returnere HTTP 400 for det brugte token. Afprøv tilsvarende et udløbet lokalt testtoken.
- Der er ikke klargjort et gyldigt token i denne kørsel. Send ikke et nyhedsbrev blot for at fremstille et token uden særskilt tilladelse.

### 4. Adminskabeloner, validering og preview

URL: `http://127.0.0.1:5178/kontrolpanel/nyhedsbrev`

- Log ind med en eksisterende **lokal testadmin**. Opret en ny skabelon med et tydeligt midlertidigt navn, emnet `Hej <Firstname>` og teksten `<Firstname> <Lastname> – <Company> – <Email>`. Kontrollér POST og HTTP 201.
- Vælg skabelonen til redigering og ændr teksten. Kontrollér PUT og HTTP 200; genindlæs og kontrollér de gemte værdier.
- Valider de fire understøttede pladsholdere: forvent `valid: true`. Tilføj `<Unknown>` og valider igen: forvent `valid: false`, `unknownVariables` og en synlig advarsel. Fjern den ukendte pladsholder og gem.
- Klik “Forhåndsvis”. Kontrollér POST til `/api/template/{id}/render`, prøveværdierne Ola/Nordmann/Nord Stand ApS/ola@example.com og den faktiske renderede tekst i svaret og UI'et.
- Annuller først sletningsbekræftelsen: ingen DELETE må sendes. Bekræft derefter sletning af **kun den nyoprettede testskabelon** og forvent HTTP 204 samt fjernelse fra listen.

### 5. Modtagere og bekræftelse uden udsendelse

URL: `http://127.0.0.1:5178/kontrolpanel/nyhedsbrev`

- Vælg en gemt testskabelon og “Udvalgte modtagere”. Kontrollér GET `/api/messages/threads?sort=name`, visning af de eksisterende samtalepartnere og brug af `customerId`, ikke ansøgnings-id. En tom samtaleliste er mulig og beviser ikke en fejl.
- Vælg de seks modtagergrupper efter tur. Kun CATEGORY skal kræve A–H; kun INDIVIDUAL skal kræve bruger-id'er. Dette er UI-verifikation; backendens segmentering er stadig ikke bevist.
- Vælg en testmodtager, og klik “Bekræft og send nyhedsbrev”. Kontrollér dialogens skabelonnavn, modtagergruppe og advarsel om rigtige mails. Vælg **Annuller**. Ingen POST `/api/newsletter` må forekomme.
- Klik ikke videre til en virkelig udsendelse. Der findes ikke et dry-run-endpoint, og bulkudsendelse er ikke tilladt i denne verifikation.

### 6. Leveringstal

URL: `http://127.0.0.1:5178/kontrolpanel/nyhedsbrev`

- Succesfulde/fejlede rigtige leveringer er **ikke testet** og kræver eksplicit tilladelse til en kontrolleret mailtest. Der må ikke køres bulkudsendelse.
- En separat test uden maillevering kan, efter at testmiljøet er bekræftet kørende, bruge **præcis én ny lokal testkonto med backendbekræftet `marketingConsent: false`**, audience INDIVIDUAL og kun den kontos id. Forvent HTTP 202 med `sentTo: 0`, `skippedNoConsent: 1`, `failedToSend: 0`. Den test er ikke udført her og siger intet om SMTP-levering eller gruppeudvælgelse.
- Ved en senere godkendt mailtest skal UI'et vise serverens tre faktiske tællere og advare, hvis `failedToSend > 0`. Genforsøg aldrig automatisk efter forbindelsesfejl.

### 7. Adgangskontrol og beskedlinks

URL: `http://127.0.0.1:5178/messages`

- Udlogget: forvent login. Log ind med almindelig testbruger og forvent `/brugerportal?tab=messages`.
- Log ud, åbn `/messages` igen, og log ind med lokal testadmin: forvent `/kontrolpanel/beskeder`.
- Som almindelig testbruger, åbn `/kontrolpanel/nyhedsbrev`: adminformularerne må ikke vises.
- Send ingen chatbeskeder som led i denne kontrol, da det kan udløse transaktionsmails.

## Ændringer og oprydning

- Denne kørsel har kun tilføjet denne rapport som kildefil. Tidligere frontendændringer er bevaret.
- Maven/Vite har genereret almindelige build- og testartefakter. Ingen genererede filer er manuelt redigeret.
- Ingen backendkode, konfiguration, credentials, afhængighedsfiler, lockfiler eller produktionsdata er ændret.
- Ingen testbrugere, skabeloner, databasefixtures, ansøgninger, chatbeskeder eller unsubscribe-tokens er oprettet/ændret.
- Ingen rigtige mails eller bulkudsendelser er forsøgt.
- Intet er committed eller pushed.

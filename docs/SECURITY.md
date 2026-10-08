# Sikkerhed i Standflow

Kort gennemgang af hvordan Standflow håndterer de typiske webtrusler. Tænkt som
reference for gruppen og til sikkerhedsrefleksionen ved eksamen. Beskriver den
nuværende tilstand i `Review` samt planlagte tiltag.

## Adgangskontrol (authorization)

- Rolle-baseret: `ANYONE`, `USER`, `ADMIN`, `OWNER` (`app.enums.Role`), håndhævet
  centralt via `AccessService.accessHandler` i `beforeMatched` — altså før hvert
  endpoint rammes, ikke pr. handler.
- Beskedsystemet: kunder (`USER`) kan kun sende til og læse deres egen tråd;
  kun chat-admin (`ADMIN`) kan se og svare i tråde (`ConversationService`
  verificerer at den autentificerede bruger faktisk er admin hhv. kunde).
- Administrative endpoints (ansøgninger, skabeloner, nyhedsbrev) kræver `ADMIN`.

## Autentificering

- Adgangskoder hashes med BCrypt (gemmes aldrig i klartekst).
- Sessioner via JWT med `tokenVersion`, så tokens kan invalideres.
- Tokens hashes (SHA-256) før opslag.

## Input-validering (server-side)

Al validering sker på serveren — frontend-validering er kun bekvemmelighed.

- Beskeder: `ConversationService.validateFields` afviser ukendte felter, og
  `requiredText` kræver at `subject`/`body` er tekst på 1–200 / 1–5000 tegn.
  Tomme og kun-whitespace-beskeder afvises (`isBlank`).
- JSON parses strengt (`FAIL_ON_TRAILING_TOKENS`, dubletnøgler afvises); ikke-objekt
  eller ugyldig JSON giver 400.
- Sorteringsparametre valideres mod en whitelist (fx tråd-sortering).

## XSS / output-escaping

- **Frontend:** React escaper automatisk al tekst der renderes som `{value}`
  (fx `{message.body}`). Der bruges **ingen** `dangerouslySetInnerHTML` nogen
  steder, så brugerinput kan ikke injicere HTML/JavaScript i UI'et.
- **E-mail:** beskedmails sendes som **ren tekst** (`email.setText(...)`), ikke
  HTML — derfor kan indhold ikke injicere markup/scripts i modtagerens mail.
- Konsekvens: beskedsystemet er ikke sårbart over for den klassiske stored-XSS,
  fordi det hverken renderer rå HTML i browseren eller i mails.

## Planlagte tiltag — fil-upload (ikke implementeret endnu)

Når stadeholdere skal kunne vedhæfte filer (fotos) og admin sende PDF'er
(stadeplan m.m.), skal følgende bygges ind fra start:

- **Filtype-validering på magic bytes** (ikke på endelse) — kun PDF og billeder
  tillades; scripts og eksekverbare filer afvises.
- **Størrelsesgrænse** pr. fil (konfigurerbar).
- **Billedkomprimering** server-side for at spare lager.
- **Dedup via SHA-256** så identiske filer ikke gemmes flere gange.
- Beskedindhold forbliver text-only; filer gemmes adskilt fra beskedteksten.

Se `projects/standflow/remaining-work-plan.md` for den fulde plan.

# Skabeloner – backend til e-mailskabeloner (admin)

## Formål

Implementer backend til at **oprette, gemme, hente, opdatere og liste e-mailskabeloner (skabeloner/udkast)**, så en medarbejder (fx Lise) kan gemme genbrugelige beskedudkast og indlæse dem igen, når der skal skrives mails i CRM'et.

Dette er en afgrænset backend-opgave. Selve mailafsendelsen (SMTP/notifikationssystemet), udfyldning af pladsholdere som `<navn>`, frontend-integration og autentificering er **separate opgaver** og ligger uden for denne ticket. Skabelonfeaturen gemmer og returnerer kun skabelonens rå indhold; den sender ingen mails og erstatter ikke pladsholdere.

## Ansvarlig og arbejdsgrundlag

- Human owner: @Tessellation93.
- Feature branch: `Tessellation93/Admin_Templates_Backend`.
- Base branch: `main`. Tess står selv for pushes og PR.
- **PR åbnes mod `Review`, aldrig mod `main`** (gruppens stående regel).
- Feature-agent: ejer udelukkende filerne i tilladelseslisten nedenfor.
- Alle backend-stier nedenfor er relative til `backend/`.

## Koordinering til gruppen – filreservation

**@Tessellation93 og agenten på skabelonfeaturen reserverer de filer, der er listet under "Tilladt scope". Andre agenter må ikke oprette, redigere, flytte eller omdøbe disse filer, før arbejdet er overdraget.** Reservationen gælder også de testfiler, som endnu ikke er oprettet.

- Skabelonfilerne (`Template*`) findes allerede i repoet som ufærdig stub (kun `id`-felt, kun GET-endpoints). De er ikke færdigtestede eller integreret.
- Denne feature **overlapper ikke** med @Rovelt123's ansøgningsopgave (`Application_Form_Backend`). Rovelts 13 reserverede filer er alle `Application*`-filer; ingen af dem rører skabelonfilerne, og ingen skabelonfiler rører Rovelts filer. De to features deler kun de fælles integrationspunkter nedenfor.
- `Routing.java` og `EntityRegistry.java` er fælles integrationspunkter. Ingen feature-agent ændrer dem. @Rovelt123 udpeger én integrationsansvarlig, som samler ændringerne sekventielt.
- Rettelser af eksisterende security-/buildfejl (manglende `User`/`UserDAO`/`UserDTO`/`UserMapper`) er en **separat opgave** med egen ejer og scope – se "Kendte forudsætninger" nedenfor.

Læs `systemPromts/Agents.md`, `systemPromts/Architecture.md` og `systemPromts/Codestandard.md` samt root-`AGENTS.md`, `ARCHITECTURE.md` og `CODESTANDARD.md` før implementering. **Root-dokumenterne er kilden til sandhed** – `systemPromts/Architecture.md` er observeret forældet (beskriver stadig bryllupsdomænet). Følg eksisterende Java 21/Javalin/Hibernate/JUnit-mønstre. Denne opgave giver ikke tilladelse til at omskrive arkitekturdokumentet eller rydde op i gammel kode.

## Beslutninger (truffet inden for scope og regelsæt)

Human owner (@Tessellation93) har truffet følgende valg. Alle holder sig inden for feature-agentens filscope og regelsættet, og de berører kun skabelon-delen, ikke andre agenters filer:

1. **Skabelonens felter:** `name`, `subject` og `body`. Afledt af mail-oplægget (kvittering, beskednotifikation og kollektive beskeder med pladsholdere som `<navn>`). Felterne lever i `Template.java` og `TemplateDTO.java`, som er feature-agentens egne filer. Skulle mail-systemet senere vise sig at skulle bruge en anden feltform, justeres mapping/DTO ved integration; det er normal integration, ikke en blocker for at gå i gang.
2. **DTO-struktur:** én `TemplateDTO`, som den eksisterende stub. Ingen ny request/response-opdeling, da det ville indføre en ny abstraktion kun til denne feature (forbudt af regelsættet), og den generiske `BaseController<E, D>` arbejder med én DTO-type.
3. **Mapper-navnet `WeddingMapper` omdøbes til `TemplateMapper`.** Regelsættet forbyder normalt at omdøbe eksisterende klasser, men gruppen har **eksplicit godkendt** denne omdøbning (5. oktober 2026), så den er en bevidst, aftalt undtagelse, ikke noget agenten fandt på selv. Omdøbningen rører kun to filer, begge feature-agentens egne: `WeddingMapper.java` (omdøbes til `TemplateMapper.java`) og de tre referencer i `TemplateController.java`. `WeddingMapper` er bekræftet brugt kun disse to steder i al kildekode på tværs af alle branches; øvrige hits er døde `.class`-filer i `target/` fra et andet projekt (SYS). Ingen anden agents filer berøres.
4. **`Notifications`-enum:** røres ikke. Den er en delt fil, og regelsættet forbyder ændring af eksisterende enums. Validerings- og fejltekster laves med `ApiException(400, ...)` og inline tekst i feature-koden, præcis som ansøgningsopgaven gør det. Eksisterende generiske tekster (fx `BODY_EMPTY`) må genbruges.

## Tilladt scope – feature-agent

Følgende **eksisterende** filer må ændres på feature-branchen:

| Fil | Ansvar |
| --- | --- |
| `src/main/java/app/entities/Template.java` | Tilføj de aftalte felter (NB: kræver schemaændring – se integration nedenfor). |
| `src/main/java/app/dtos/TemplateDTO.java` | Tilføj de aftalte input-/outputfelter. |
| `src/main/java/app/services/TemplateService.java` | Validering, normalisering og oprettelse/opdatering/indlæsning. I dag tom. |
| `src/main/java/app/controllers/TemplateController.java` | Tilføj create/update/list/get-endpoints i den eksisterende `registerRoutes()`. Opdater de tre `WeddingMapper`-referencer til `TemplateMapper` (beslutning 3). |
| `src/main/java/app/mappers/WeddingMapper.java` | Omdøbes til `TemplateMapper.java` (godkendt, beslutning 3). Udvid mapping til de nye felter. |

Følgende **nye** filer må oprettes:

| Fil | Ansvar |
| --- | --- |
| `src/main/java/app/mappers/TemplateMapper.java` | Den omdøbte mapper (erstatter `WeddingMapper.java`). Mapper `Template` ↔ `TemplateDTO` inkl. de nye felter. |
| `src/test/java/app/controllers/TemplateControllerTest.java` | HTTP-kontrakt og ugyldigt input. |
| `src/test/java/app/services/TemplateServiceTest.java` | Validering og serverstyrede værdier. |
| `src/test/java/app/daos/TemplateDAOTest.java` | Persistens, genindlæsning og rollback. |
| `src/test/java/app/support/TemplateTestSupport.java` | Isoleret H2-/Javalin-setup for denne features tests. |

`TemplateDAO.java` arver allerede fuld CRUD fra `EntityManagerDAO<Template>` og skal sandsynligvis **ikke** ændres – genbrug den, implementér ikke ny persistenslogik. Ingen wildcard-tilladelser. Hjælpemetoder placeres i ovenstående filer.

## Grænseflade til mail-systemet (separat opgave)

- Skabelonfeaturen gemmer og returnerer kun skabelonens rå indhold. Den **sender ikke mails** og **udfylder ikke pladsholdere**.
- Mail-systemet (SMTP, del 2) indlæser en skabelon via `GET /template/{id}`, udfylder pladsholdere og sender. Denne opgave leverer altså datalaget, ikke afsendelsen.
- Feltkontrakten (`name`/`subject`/`body`) er fastlåst her (beslutning 1). Viser det sig at mail-systemet skal bruge en anden form, justeres det ved integration.

## Reserveret integration – ikke feature-agentens filer

Disse ændringer er nødvendige for en fungerende løsning, men er **ikke** automatisk autoriseret ved denne opgave. Human owner tildeler integrationsansvaret og godkender undtagelsen for database før implementering.

| Eksisterende fil/område | Eneste planlagte integration | Ejer |
| --- | --- | --- |
| `src/main/java/app/configs/EntityRegistry.java` | Registrér `Template.class` via `configuration.addAnnotatedClass(Template.class)`. I dag registreres ingen entiteter – `Template` er derfor ikke koblet til Hibernate endnu. Bevar andre registreringer. | Integrationsansvarlig |
| `src/main/java/app/server/Routing.java` | **Forventes ikke ændret.** `Routing` kalder allerede `TemplateController.registerRoutes()`, så nye endpoints tilføjes inde i den metode uden at røre denne fil. Medtages her kun som delt fil, der ikke må ændres af feature-agenten. | Integrationsansvarlig |
| Databaseskema | Tilføj kun de nye kolonner på den eksisterende `template`-tabel via projektets aftalte schema-/migrationsproces. | Human owner/databaseansvarlig |

Der er ikke identificeret en eksisterende migrationsmappe i backend. `HibernateConfig` bruger `hbm2ddl.auto = "update"`, men opfind ikke migrationsfiler og ændr ikke Hibernate-konfigurationen for at omgå noget. Før databaseintegration skal den konkrete fremgangsmåde tilføjes til ticketen. Den generiske skabelons forbud mod alle schemaændringer kan ikke opfyldes samtidig med ny vedvarende lagring: de nye `template`-kolonner er derfor en eksplicit integrationsafhængighed. Eksisterende tabeller, entiteter, relationer og nøgler ændres ikke.

## Må ikke ændres

Alle filer uden for tilladelseslisten er skrivebeskyttede for feature-agenten, herunder:

- `pom.xml`, lockfiler, build- og dependencyfiler.
- `.gitignore`, `README.md`, `.github/**` og eventuelle Docker-/CI-filer.
- `src/main/resources/**`, miljøvariabler og delte konfigurationsfiler.
- `src/main/java/app/configs/**`, `src/main/java/app/server/**` og `src/main/java/app/Main.java`.
- `src/main/java/app/security/**` og autentifikation/autorisationsarkitekturen.
- Eksisterende enums (inkl. `Notifications`, `Role`) – jf. beslutning 4.
- **Alle `Application*`-filer** (reserveret af @Rovelt123's ansøgningsopgave) samt øvrige eksisterende controllers, services, DAO'er, DTO'er, mappers, entiteter, exceptions og utils, der ikke er på min liste.
- `src/test/java/app/SetupTest.java` og `src/test/java/app/daos/generic/**`.
- Hele `frontend/` og andre opgavebeskrivelser i `systemPromts/`.
- Genererede filer, herunder `target/**`, må ikke redigeres manuelt.

Der må ikke tilføjes dependencies, nye frameworks, ny generisk abstraktion kun til denne feature, generel refaktorering eller ændringer af eksisterende API-kontrakter.

## API-kontrakt

Alle endpoints registreres inde i den eksisterende `TemplateController.registerRoutes()`. Rolle: behold `Role.USER` som i den nuværende stub (reel håndhævelse afhænger af den separate User/security-reparation).

| Metode | Endpoint | Formål |
| --- | --- | --- |
| `GET` | `/api/template` | List alle skabeloner. (findes) |
| `GET` | `/api/template/{id}` | Hent én skabelon til indlæsning. (findes) |
| `POST` | `/api/template` | Opret/gem en ny skabelon. (ny) |
| `PUT` | `/api/template/{id}` | Opdater en eksisterende skabelon. (ny) |

Feltkontrakten er fastlåst (jf. beslutning 1):

| JSON-felt | Type | Validering |
| --- | --- | --- |
| `name` | string | Påkrævet, 1–150 tegn. Skabelonens navn/titel til visning i listen. |
| `subject` | string | Påkrævet, 1–200 tegn. Mailens emnefelt. |
| `body` | string | Påkrævet, 1–5000 tegn. Fri tekst, kan indeholde pladsholdere som `<navn>`. |

`POST`/`PUT`-requesten indeholder ikke `id` (server-/sti-styret). Manglende eller tomme obligatoriske felter og forkerte JSON-typer afvises med 400 via eksisterende `ApiException(400, ...)` og den eksisterende fejlhandlers tekstformat. Svaret returnerer den gemte skabelon inkl. `id`. Pladsholdere gemmes råt og udfyldes ikke her.

## Implementeringskrav

- Flow: controller → service → DAO → Hibernate. Controlleren udfører ikke databaseoperationer direkte.
- Genbrug `EntityManagerDAO` og det eksisterende transaktionsmønster. Service/DAO skal kunne injiceres via konstruktør til test, som i eksisterende kode.
- Map eksplicit via `TemplateMapper` (den omdøbte mapper); deserialisér aldrig request direkte til JPA-entiteten.
- Genbrug eksisterende fejlbehandling (`ApiException`); indfør ikke en ny fejlkontrakt.
- Introducer ikke ny abstraktion kun til denne feature.
- Ingen schemaændring ud over de aftalte `template`-kolonner (integrationsafhængighed).
- Følg `CODESTANDARD.md`: PascalCase-klasser, camelCase-metoder og `//--------------------------------------------------------------` mellem metoder. Ret ikke eksisterende filer for at ensrette formattering.

## Tests og verificering

Brug eksisterende JUnit 5 og H2. HTTP-tests kan bruge JDK's HTTP-klient og en Javalin-instans på en ledig port; ingen nye testdependencies. Luk server/EntityManager/EntityManagerFactory efter tests.

Test som minimum:

1. Gyldig `POST` giver den gemte skabelon med server-genereret `id`; data kan genindlæses fra databasen i en ny persistence context.
2. Alle obligatoriske felter testes for manglende, null og blank værdi samt maksimumgrænser.
3. `GET /api/template/{id}` returnerer en gemt skabelon; ukendt id håndteres med eksisterende fejlmønster.
4. `GET /api/template` returnerer listen (tom liste når ingen findes).
5. `PUT` opdaterer en eksisterende skabelon; ugyldigt input afvises uden at ændre data.
6. Ugyldig JSON og ukendte felter giver 400 uden at oprette/ændre en række.

Kør fra `backend/` og registrér kommandoer og resultater i PR'en:

```text
mvn test
mvn package
```

### Kendte forudsætninger i den nuværende kode

Baseline-build fejler aktuelt (observeret af @Rovelt123, 5. oktober 2026): 21 kompileringsfejl pga. manglende `User`, `UserDAO`, `UserDTO` og `UserMapper` i eksisterende security-kode (jf. `security/readme.md`: "Husk at lave user object"). **Agenten vil ramme samme fejl.** Kør baseline-verifikation før implementering. Hvis eksisterende fejl blokerer build/test: registrér fejlene og giv human owner en separat reparationsopgave. Opret ikke dummy-klasser, slet ikke eksisterende tests, og ret ikke security eller fælles testsetup som en del af denne feature. Markér blokeret verificering ærligt; opgaven er ikke færdig, før nødvendige checks kan køre og bestå efter integration.

## Acceptkriterier / Definition of done

- [ ] Implementeringen følger de trufne beslutninger: felter `name`/`subject`/`body`, én `TemplateDTO`, `WeddingMapper` omdøbt til `TemplateMapper` (godkendt), `Notifications` urørt.
- [ ] En skabelon kan oprettes, gemmes, hentes, opdateres og listes efter den aftalte kontrakt.
- [ ] Gyldig `POST` gemmer skabelonen og returnerer den med server-genereret `id`.
- [ ] Ugyldigt input giver 400 uden databaseændringer; eksisterende fejlbehandling genbruges.
- [ ] Pladsholdere gemmes råt; der sendes ingen mails og udfyldes ingen pladsholdere i denne feature.
- [ ] Feature-agentens diff indeholder kun de navngivne filer; ingen `Application*`- eller fælles filer er ændret.
- [ ] Integration i `EntityRegistry` og den aftalte databaseproces er udført af integrationsansvarlig og verificeret.
- [ ] Relevante tests, samlet build og eksisterende kvalitetschecks består. Baseline-fejl er løst i separat scope, hvis de blokerede.
- [ ] Eksisterende endpoints, frontend, delte filer og andre agenters arbejde er bevaret bortset fra udtrykkeligt godkendt integration.

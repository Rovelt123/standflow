Jeg ville gøre den mere **spec-drevet** end din nuværende version: agenten skal ikke selv beslutte, hvad en thread er, hvem der må se hvad, hvordan `read` virker eller hvilke endpoints der mangler.

Her er den version, jeg ville give direkte til kodeagenten:

```md
# Ticket 3 – Backend: chat mellem kunde og admin

## Formål

Implementér backend-understøttelse for tovejs chat mellem en registreret kunde og admin/Lise.

Kunden skal kunne:

- sende en besked til admin
- hente sin egen samtale
- modtage svar fra admin

Admin skal kunne:

- se en oversigt over alle kundesamtaler
- se hvilke samtaler der indeholder ulæste kundebeskeder
- sortere samtaleoversigten
- åbne én kundes samtale
- svare kunden
- markere kundens beskeder som læst

Denne ticket ejer den nye `Message`-entitet.

Der findes allerede en klasse med navnet `MessageService`, som bruges til opbygning af tekstbeskeder. Den må ikke ændres eller genbruges til denne feature.

Den nye business service skal hedde:

`ConversationService`

---

## Ansvarlig

Human owner: `@Linus-llm`

Agent branch:

`Linus-llm/chat_backend`

Base branch:

`Review`

PR target:

`Review`

---

# Domænebeslutninger

## Chat er knyttet til User

Chatten er konto-baseret.

Både kunde og admin repræsenteres af den eksisterende `User`-entitet.

`Application` indgår ikke i chatmodellen.

En kunde skal være registreret og authenticated for at bruge chatten.

En `Message` skal derfor referere til eksisterende `User`-entiteter som:

- sender
- recipient

Eksisterende `User`, `Application` eller security-entiteter må ikke ændres som del af denne ticket.

---

## Admin

Admin er en eksisterende `User` med `Role.ADMIN` eller `Role.OWNER`.

Kunden må ikke selv kunne vælge eller manipulere sender-identitet.

Authenticated user hentes fra den eksisterende authentication-context.

Hvis repositoryets nuværende model ikke giver en entydig måde at identificere den admin-bruger, som kundebeskeder skal sendes til, må dette **ikke gættes**.

Stop i så fald implementeringen og rapportér blockeren.

---

# Thread-begreb

Der skal **ikke** oprettes en `Thread`- eller `Conversation`-entity.

En thread er et API-/service-begreb og består af alle `Message`-entiteter mellem én kunde og admin.

`ConversationService` har ansvaret for at gruppere beskeder til threads.

Eksempel:

```text
Kunde A -> Admin
Admin   -> Kunde A
Kunde A -> Admin
```

udgør én thread for Kunde A.

---

# Message-entitet

Opret:

`backend/src/main/java/app/entities/Message.java`

Entiteten skal gemmes i tabellen:

`messages`

Brug JPA på samme måde som repositoryets eksisterende entities.

En message skal mindst indeholde:

```text
id
sender
recipient
subject
body
createdAt
read
```

Forventet konceptuel database-model:

```text
messages

id              UUID / PK
sender_id       FK -> users.id
recipient_id    FK -> users.id
subject
body
created_at
is_read
```

`sender` og `recipient` skal være relationer til `User`.

Der skal ikke tilføjes collections som `messages` til `User.java`.

---

# Read/unread-semantik

`read` beskriver om beskeden er blevet læst af dens recipient.

En ny besked oprettes som:

```text
read = false
```

Når admin markerer en kundetråd som læst:

- kun ulæste beskeder
- sendt FRA den pågældende kunde
- TIL admin

markeres som læst.

Admins egne beskeder til kunden må ikke ændres.

En thread har:

```text
unread = true
```

hvis mindst én besked fra kunden til admin stadig har:

```text
read = false
```

---

# Sortering

Admin sorterer **thread-listen**, ikke rækkefølgen af messages inde i en thread.

Understøt:

```text
sort=name
sort=company
sort=subject
sort=date
```

Definitioner:

### name

Sorter efter kundens navn.

### company

Sorter efter kundens `User.company`.

### subject

Sorter efter threadens seneste/relevante subject.

### date

Sorter efter tidspunktet på threadens seneste besked.

Default:

```text
sort=date
```

Nyeste samtaler først.

Messages inde i én thread returneres kronologisk:

```text
ældste -> nyeste
```

Ukendt `sort`-værdi skal give HTTP 400 via `ApiException`.

---

# API-kontrakt

## Kunde – send besked

```http
POST /api/messages
```

Role:

```text
USER
```

Request:

```json
{
  "subject": "Spørgsmål til min stand",
  "body": "Hej, jeg har et spørgsmål..."
}
```

Authenticated kunde er altid sender.

Klienten må ikke sende:

```text
senderId
recipientId
read
createdAt
```

Serveren bestemmer disse værdier.

Response:

```text
HTTP 201
```

---

## Kunde – hent egen thread

```http
GET /api/messages/mine
```

Role:

```text
USER
```

Returnerer kun messages hvor authenticated customer er deltager.

Kundens identitet skal komme fra authentication context.

Der må ikke accepteres et vilkårligt customer/user id fra klienten på dette endpoint.

Messages returneres kronologisk.

Response:

```text
HTTP 200
```

En kunde må aldrig kunne hente en anden kundes messages.

---

# Admin – hent thread-oversigt

```http
GET /api/messages/threads
```

Role:

```text
ADMIN
```

eller eksisterende OWNER-overstyring via security-systemet.

Optional query parameter:

```text
?sort=name
?sort=company
?sort=subject
?sort=date
```

Eksempel:

```http
GET /api/messages/threads?sort=company
```

Returnerer én summary per kunde/thread.

Eksempel på konceptuel response:

```json
[
  {
    "customerId": "uuid",
    "name": "Peter Jensen",
    "company": "Peters Keramik",
    "subject": "Spørgsmål til stand",
    "lastMessage": "Hej, jeg ville lige høre...",
    "lastMessageAt": "2026-10-06T14:21:32",
    "unread": true
  }
]
```

Det er ikke et krav at oprette en separat ThreadDTO, medmindre det kan gøres inden for tilladt scope og uden unødvendig abstraktion.

Foretræk den simpleste løsning, der følger eksisterende kodepraksis.

---

# Admin – åbn én thread

```http
GET /api/messages/threads/{customerId}
```

Role:

```text
ADMIN
```

Returnerer alle messages mellem admin og den valgte kunde.

Messages sorteres:

```text
ældste -> nyeste
```

Response:

```text
HTTP 200
```

Hvis kunden ikke findes:

```text
HTTP 404
```

---

# Admin – svar kunde

```http
POST /api/messages/threads/{customerId}
```

Role:

```text
ADMIN
```

Request:

```json
{
  "body": "Hej Peter, det kan du sagtens."
}
```

Authenticated admin er sender.

`customerId` identificerer recipient.

Klienten må ikke kontrollere `senderId`.

Response:

```text
HTTP 201
```

Hvis threaden allerede har et subject, skal svaret genbruge threadens eksisterende subject.

Agenten må ikke introducere en `Conversation`-entity alene for at gemme subject.

---

# Admin – markér thread som læst

```http
PATCH /api/messages/threads/{customerId}/read
```

Role:

```text
ADMIN
```

Markerer alle ulæste messages:

```text
sender = customerId
recipient = authenticated admin
read = false
```

som:

```text
read = true
```

Admins egne beskeder påvirkes ikke.

Response:

```text
HTTP 204
```

---

# Inputvalidering

Ugyldigt input skal give:

```text
HTTP 400
```

via eksisterende:

`ApiException`

Minimumskrav:

### subject

- required ved kundens nye besked
- må ikke være null
- må ikke være blank efter trim
- max 200 tegn

### body

- required
- må ikke være null
- må ikke være blank
- max 5000 tegn

### customerId

- skal være gyldigt UUID hvor endpointet kræver UUID
- ugyldigt UUID -> 400

### sort

Kun følgende værdier accepteres:

```text
name
company
subject
date
```

Alt andet giver 400.

Forkerte JSON-datatyper, malformed JSON og ugyldige request-felter skal håndteres efter eksisterende projektmønster og returnere 400.

---

# Authorization

Authorization skal håndhæves backend-side.

## Kunde

En kunde må:

- sende en besked som sig selv
- hente sin egen thread

En kunde må ikke:

- angive anden sender
- hente andre kunders threads
- kalde admin thread-listen
- åbne en anden kundes admin-thread
- svare på vegne af admin

## Admin

Admin må:

- hente thread-listen
- åbne en kundethread
- svare kunden
- markere kundens beskeder som læst

Genbrug repositoryets eksisterende role/security-mekanisme.

`security/**` må ikke ændres.

---

# Arkitektur

Følg dette flow:

```text
MessageController
        |
        v
ConversationService
        |
        v
MessageDAO
        |
        v
EntityManager / Hibernate
```

Controlleren må ikke kommunikere direkte med databasen.

Business logic, authorization omkring ejerskab og grouping af threads skal ligge i `ConversationService` hvor passende.

Persistence/query-logik skal ligge i `MessageDAO`.

---

# Nye produktionsfiler

Tilladt at oprette:

```text
backend/src/main/java/app/entities/Message.java
backend/src/main/java/app/daos/MessageDAO.java
backend/src/main/java/app/dtos/MessageDTO.java
backend/src/main/java/app/mappers/MessageMapper.java
backend/src/main/java/app/services/ConversationService.java
backend/src/main/java/app/controllers/MessageController.java
```

---

# Testfiler

Opret:

```text
backend/src/test/java/app/controllers/MessageControllerTest.java
backend/src/test/java/app/services/ConversationServiceTest.java
backend/src/test/java/app/daos/MessageDAOTest.java
backend/src/test/java/app/support/MessageTestSupport.java
```

Tilpas packages til repositoryets eksisterende teststruktur.

---

# Må ikke ændres

Feature-agenten må ikke ændre:

```text
pom.xml
.gitignore
README.md
.github/**
backend/src/main/resources/**
backend/src/main/java/app/configs/**
backend/src/main/java/app/server/**
backend/src/main/java/app/Main.java
backend/src/main/java/app/security/**
backend/target/**
```

Må heller ikke ændre:

- eksisterende `MessageService`
- `User`
- `Application`
- eksisterende controllers
- eksisterende services
- eksisterende entities
- eksisterende mappers
- eksisterende DAO'er

`Template`-feature må læses som reference, men ikke ændres.

Ingen:

- nye dependencies
- generel refaktorering
- filomdøbning
- arkitekturændringer uden for denne ticket

---

# Eksisterende kode der bør bruges som reference

Læs før implementation:

```text
AGENTS.md
ARCHITECTURE.md
CODESTANDARD.md

backend/src/main/java/app/entities/User.java
backend/src/main/java/app/entities/Application.java

backend/src/main/java/app/daos/generic/EntityManagerDAO.java
backend/src/main/java/app/daos/UserDAO.java

backend/src/main/java/app/controllers/TemplateController.java
backend/src/main/java/app/services/TemplateService.java

backend/src/main/java/app/security/AccessService.java
backend/src/main/java/app/security/SecurityService.java

backend/src/main/java/app/exceptions/ApiException.java

backend/src/main/java/app/configs/TestHibernateConfig.java
```

Følg eksisterende mønstre frem for at introducere nye.

Følg `CODESTANDARD.md`.

---

# Persistence guardrails

Genbrug:

`EntityManagerDAO`

Genbrug repositoryets eksisterende transaktionsmønster.

Brug JPQL eller repositoryets eksisterende query-strategi.

Undgå dynamisk JPQL baseret direkte på ikke-valideret brugerinput.

Sorteringsparameteren skal mappes fra en whitelist:

```text
name
company
subject
date
```

til kendte queries/properties.

---

# Mapper

Mapping mellem:

```text
Message
```

og:

```text
MessageDTO
```

skal ske eksplicit gennem:

`MessageMapper`

Controlleren skal ikke bygge persistence entities direkte.

Ingen ny generic mapper-abstraktion må introduceres.

---

# Tests

Brug isoleret H2 gennem:

`MessageTestSupport`

Feature-testene må ikke kræve ændring af den fælles `EntityRegistry`.

Test-support skal registrere de entities, feature-testene har brug for, lokalt til tests.

Minimum testdækning:

### MessageDAOTest

- message kan gemmes
- messages for korrekt kunde kan hentes
- messages sorteres kronologisk
- unread kan findes
- read-state kan opdateres
- thread-data kan grupperes/hentes som nødvendigt
- sortering efter relevante felter virker

### ConversationServiceTest

- kunde kan sende message
- sender bestemmes af authenticated/current user input til servicen
- kunde kan hente egen thread
- kunde får ikke adgang til anden kundes data
- admin kan hente alle threads
- admin kan åbne én thread
- admin kan svare
- admin-svar får korrekt sender og recipient
- unread beregnes korrekt
- mark-as-read påvirker kun relevante messages
- blank subject afvises
- blank body afvises
- for langt subject/body afvises
- ugyldig sortering afvises

### MessageControllerTest

Test HTTP-kontrakten for:

```text
POST /api/messages
GET /api/messages/mine
GET /api/messages/threads
GET /api/messages/threads/{customerId}
POST /api/messages/threads/{customerId}
PATCH /api/messages/threads/{customerId}/read
```

Test minimum:

- korrekte statuskoder
- ugyldigt JSON -> 400
- ugyldigt input -> 400
- unauthorized/forbidden adgang håndteres af eksisterende security-model
- kunde kan ikke hente anden kundes data
- admin routes virker med korrekt role

---

# Integration – udføres ikke af feature-agenten

Integrationsansvarlig foretager efter merge/integration:

## Routing.java

Registrér:

`MessageController`

under `/api`.

## EntityRegistry.java

Tilføj:

```java
configuration.addAnnotatedClass(Message.class);
```

## Database

Hibernate/JPA skal kunne oprette:

`messages`

-tabellen ud fra `Message`-entity mapping.

Feature-agenten må ikke ændre disse integrationsfiler.

---

# Afhængighed til ticket 5

Når admin sender en ny besked til en kunde, skal dette senere kunne udløse en notifikationsmail.

Mailimplementering er **ikke** en del af denne ticket.

Denne ticket må ikke implementere SMTP/mail-logik.

Hold `ConversationService`-implementationen simpel og uden ny event-/notification-arkitektur.

Ticket 5 integrerer mailnotifikation separat.

---

# Acceptkriterier

- [ ] `Message` eksisterer som JPA-entity med egen `messages`-tabel.
- [ ] Message har sender, recipient, subject, body, timestamp og read-state.
- [ ] Sender og recipient refererer til eksisterende `User`.
- [ ] Der oprettes ingen `Thread`/`Conversation`-entity.
- [ ] Kunde kan sende message.
- [ ] Kunde kan hente sin egen thread.
- [ ] Kunde kan ikke hente andre kunders messages.
- [ ] Admin kan hente thread-oversigt.
- [ ] Admin kan åbne én kundethread.
- [ ] Admin kan svare kunden.
- [ ] Admin kan se unread thread-state.
- [ ] Admin kan markere kundebeskeder som læst.
- [ ] Sortering efter name virker.
- [ ] Sortering efter company virker.
- [ ] Sortering efter subject virker.
- [ ] Sortering efter date virker.
- [ ] Ugyldigt input giver 400 via `ApiException`.
- [ ] Controller tilgår ikke persistence direkte.
- [ ] Explicit mapping sker via `MessageMapper`.
- [ ] `MessageService` er urørt.
- [ ] Ingen filer uden for tilladt scope er ændret.
- [ ] `MessageDAOTest` består.
- [ ] `ConversationServiceTest` består.
- [ ] `MessageControllerTest` består.
- [ ] Tests bruger isoleret H2 via `MessageTestSupport`.

---

# Arbejdsgang for agenten

Før kode ændres:

1. Læs `AGENTS.md`.
2. Læs relevante dele af `ARCHITECTURE.md`.
3. Læs `CODESTANDARD.md`.
4. Inspicér de eksisterende implementationsmønstre nævnt ovenfor.
5. Kontrollér branch/base.
6. Kontrollér at de nødvendige antagelser i denne ticket kan opfyldes.

Implementér derefter den mindste løsning, der opfylder ticketen.

Hvis et krav kræver ændring af en fil uden for tilladt scope:

**Stop og rapportér det. Gæt ikke og udvid ikke scope selv.**

---

# Definition of Done

Før opgaven erklæres færdig:

1. Kør relevante backend-tests.
2. Kør hele backend-test-suiten hvis muligt.
3. Verificér alle acceptkriterier.
4. Kontrollér `git diff`.
5. Kontrollér at der ikke er ændret filer uden for ticketens scope.
6. Rapportér:
   - hvilke filer der blev oprettet
   - hvilke tests der blev kørt
   - testresultater
   - eventuelle blockers eller antagelser

Opret ikke integration changes til `Routing.java` eller `EntityRegistry.java`.
```

Der er især **én linje, jeg ville beholde**, selvom den virker lidt defensiv:

> *Hvis repositoryets nuværende model ikke giver en entydig måde at identificere den admin-bruger, som kundebeskeder skal sendes til, må dette ikke gættes.*

For ellers er der stadig et hul: `POST /api/messages` fortæller agenten, at kunden skriver "til admin", men hvis databasen har to admins, skal agenten ellers selv finde på, hvilken `recipient_id` der skal gemmes.

Resten er efter min vurdering tæt på en ticket, hvor du kan give den til agenten og forvente, at **to forskellige kodeagenter kommer frem til nogenlunde samme løsning**. Det er et godt tegn på, at spec'en er blevet tilstrækkeligt præcis.
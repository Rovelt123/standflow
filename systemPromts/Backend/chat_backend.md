# Ticket backend på chat mellem kunde og admin
## Formål
Toevejs kommunikation i platformen. Kunden skal kunne skrive til Lise, og Lise skal kunne svare fra sit kundekartotek. Nye beskeder skal være tydelige for Lise, og hun skal kunne sortere sine beskeder for hurtigt at navigere.

Systemet forventer én `User` med `Role.ADMIN`, som fungerer som chat-admin.

## Ansvarlig
Human owner: @Linus-llm Agent branch: Linus-llm/chat_backend Base branch: Review. PR mod Review.

## Før kodning
Læs AGENTS.md, ARCHITECTURE.md og CODESTANDARD.md

## Eksisterende kode og projektstruktur

Før implementering skal agenten gennemgå den eksisterende backend-kode og følge de patterns, der allerede bruges i projektet.

Læs især relevante eksisterende filer inden for:

- entities
- controllers
- services
- daos
- dtos
- mappers
- security
- configs
- tests

Genbrug eksisterende struktur og conventions frem for at introducere nye patterns.

Featuret skal passe ind i den nuværende arkitektur, herunder:

- controller -> service -> DAO
- eksisterende JPA/Hibernate-pattern
- eksisterende `EntityManagerDAO`
- eksisterende `ApiException` / `ErrorHandler`
- eksisterende authentication/authorization
- eksisterende mapper/DTO-pattern
- eksisterende test- og H2-pattern

Hvis der findes flere mulige implementationer, vælg den der bedst matcher den eksisterende kodebase og kræver færrest arkitekturændringer.

Der må ikke laves generel refaktorering af eksisterende kode som del af denne ticket.

## Må ikke ændres

Følgende filer og områder må ikke ændres som del af denne ticket:

- `pom.xml`
- `.gitignore`
- `README.md`
- `.github/**`
- `backend/src/main/resources/**`
- `backend/src/main/java/app/Main.java`
- `backend/src/main/java/app/security/**`
- `backend/target/**`

Eksisterende featurekode må heller ikke ændres, medmindre det er eksplicit nødvendigt for denne ticket:

- eksisterende `MessageService`
- `User.java`
- `Application.java`
- eksisterende controllers
- eksisterende services
- eksisterende DAO'er
- eksisterende mappers
- eksisterende DTO'er

Følgende integrationsfiler må ikke ændres af feature-agenten:

- `backend/src/main/java/app/server/Routing.java`
- `backend/src/main/java/app/configs/EntityRegistry.java`

Disse ændringer udføres senere af integrationsansvarlig.

Der må heller ikke:

- tilføjes nye dependencies
- laves generel refaktorering
- omdøbes eksisterende filer eller klasser
- ændres public API-kontrakter uden for chat-featuret
- introduceres nye arkitekturlag eller frameworks
- ændres authentication- eller authorization-arkitektur

Hvis implementationen kræver en ændring uden for dette scope, skal agenten stoppe og rapportere behovet i stedet for selv at udvide scope.

## Message-entitet og database
Opret en ny JPA-entitet:
- `backend/src/main/java/app/entities/Message.java`

Entiteten skal følge samme JPA-mønster som repositoryets eksisterende entities og gemmes i tabellen: messages

Message har to unidirectional ManyToOne-relationer til User: sender og recipient.

Message-entitet:
- id - UUID primary key, genereret af JPA
- sender - den User, der sender beskeden
- recipient - den user, der modtager beskeden
- body - beskeden indhold
- subject - beskeden emne
- createdAt - tidspunkt og dato
- read - læst/ulæst

`sender` og `recipient` skal begge være relationer til den eksisterende `User`-entitet

Konceptuel tabelstruktur:

messages
--------------------------------
id            UUID PK
sender_id     FK -> users.id
recipient_id  FK -> users.id
subject
body
created_at
is_read

Krav:
- sender må ikke være null
- recipient må ikke være null
- subject må ikke være null
- body må ikke være null
- createdAt må ikke være null
- nye beskeder oprettes med read = false
- User.java må ikke ændres for at tilføje en collection af messages

## Authorization i chat-featuret

`ConversationService` skal håndhæve featurets egne kunde- og chat-admin-regler.

Hvis en request når `ConversationService`, men den authenticated bruger ikke opfylder featurets kunde- eller chat-admin-krav, returneres:

`HTTP 403`

via `ApiException`.

Requests, der afvises tidligere af projektets eksisterende security-lag, beholder security-lagets eksisterende response-adfærd.

Den eksisterende authentication- og authorization-arkitektur må ikke ændres.

## Kundedefinition

En kunde i chat-featuret er en bruger med `Role.USER`, som hverken har `Role.ADMIN` eller `Role.OWNER`.

Kunde-endpoints kræver, at den authenticated bruger opfylder denne definition.

Hvis en authenticated bruger forsøger at bruge et kunde-endpoint uden at opfylde kundedefinitionen, returneres:

`HTTP 403`

via `ApiException` i `ConversationService`.

Hvis `customerId` på et admin-endpoint peger på en bruger, der ikke opfylder kundedefinitionen, returneres:

`HTTP 400`.

## Endpoints / API-adfærd

### Kunde sender besked til admin


`POST /api/messages`

Role: `USER`
Request:
```json
{
  "subject": "Spørgsmål til min stand",
  "body": "Hej, jeg har et spørgsmål..."
}
```
Den authenticated User er altid sender.
Modtageren er systemets admin-bruger med Role.ADMIN.
Klienten må ikke selv sende eller vælge:
- senderId
- recipientId
- createdAt
- read
Serveren sætter disse værdier.

Response:

`HTTP 201`

### Kunde henter egen samtale

`GET /api/messages/mine`

Role: `USER`

Returnerer alle beskeder mellem den authenticated kunde og admin.

Kundens identitet hentes fra authentication context og må ikke kunne angives som query- eller path-parameter.

Messages returneres kronologisk fra ældste til nyeste.

Response:

`HTTP 200`

### Admin henter samtaleoversigt

`GET /api/messages/threads`

Role: `ADMIN`

Returnerer én `ThreadDTO` per kunde.

En thread består af alle beskeder mellem én kunde og admin.

`Thread` er ikke en database-entitet, men et API-/service-begreb, der bruges til at gruppere `Message`-entiteter pr. kunde i adminens samtaleoversigt.

Der skal derfor ikke oprettes en `Thread`- eller `Conversation`-entity.

Endpointet understøtter query parameter:

- `?sort=name`
- `?sort=company`
- `?sort=subject`
- `?sort=date`

Hvis `sort` ikke angives, sorteres efter `date`.

Sortering:

- `name` – kundens navn, alfabetisk
- `company` – kundens virksomhed, alfabetisk
- `subject` – subject på seneste besked i threaden, alfabetisk
- `date` – tidspunktet på seneste besked, nyeste først

Ukendt sorteringsværdi giver HTTP 400 via `ApiException`.

Response:

`HTTP 200`

### Admin åbner én kundesamtale

`GET /api/messages/threads/{customerId}`

Role: `ADMIN`

Returnerer alle beskeder mellem authenticated admin og den valgte kunde.

Beskeder returneres kronologisk fra ældste til nyeste.

Response:

`HTTP 200`


### Admin starter eller svarer i en samtale

`POST /api/messages/threads/{customerId}`

Role: `ADMIN`

Endpointet bruges både til:

- at starte en ny samtale med en kunde
- at svare i en eksisterende samtale

`customerId` bestemmer hvilken kunde beskeden sendes til.

Den authenticated bruger skal være systemets ene chat-admin med `Role.ADMIN`.

### Hvis samtalen ikke findes

Request:

```json
{
  "subject": "Praktisk information om din stand",
  "body": "Hej Peter, jeg har lidt information til dig..."
}
```
Krav:

- `subject` er required
- `subject` må ikke være blank
- `subject` må højst være 200 tegn
- `body` er required
- `body` må ikke være blank
- `body` må højst være 5000 tegn

Serveren sætter:

- `sender` = authenticated chat-admin
- `recipient` = kunden fra `customerId`
- `createdAt` = serverens tidspunkt
- `read = false`

Denne besked opretter den første `Message` mellem kunden og admin og starter dermed samtalen.

### Hvis samtalen allerede findes

Request:

```json
{
  "body": "Hej Peter, det kan du sagtens."
}
```

Krav:
- body er required
- body må ikke være blank
- body må højst være 5000 tegn
- klienten må ikke sende eller ændre subject
subject genbruges fra den seneste besked i samtalen.
Serveren sætter:
- sender = authenticated chat-admin
- recipient = kunden fra customerId
- createdAt = serverens tidspunkt
- read = false
Response
Ved succes:
HTTP 201
Returnerer den oprettede MessageDTO.
Fejl
- ugyldigt customerId -> HTTP 400
- kunden findes ikke -> HTTP 404
- den valgte bruger er ikke en kunde -> HTTP 400
- authenticated bruger er ikke systemets chat-admin -> HTTP 403
- manglende subject ved oprettelse af ny samtale -> HTTP 400
- manglende eller ugyldig body -> HTTP 400
- ukendte request-felter -> HTTP 400
- server-controlled request-felter -> HTTP 400
Server-controlled felter omfatter:
- id
- senderId
- recipientId
- createdAt
- read
Alle fejl håndteres via projektets eksisterende ApiException-pattern.

### Admin markerer samtale som læst

`PATCH /api/messages/threads/{customerId}/read`

Role: `ADMIN`

Markerer kun ulæste beskeder:

- sendt fra den valgte kunde
- sendt til authenticated admin
- hvor `read = false`

som læst.

Admins egne beskeder til kunden må ikke ændres.

Response:

`HTTP 204`

## Response-format

### Kunde sender besked

`POST /api/messages`

Response:

`HTTP 201`

Returnerer den oprettede `MessageDTO`.

### Kunde henter egen samtale

`GET /api/messages/mine`

Response:

`HTTP 200`

Returnerer:

`List<MessageDTO>`

### Admin henter samtaleoversigt

`GET /api/messages/threads`

Response:

`HTTP 200`

Returnerer:

`List<ThreadDTO>`

### Admin åbner kundesamtale

`GET /api/messages/threads/{customerId}`

Response:

`HTTP 200`

Returnerer:

`List<MessageDTO>`

### Admin svarer kunde

`POST /api/messages/threads/{customerId}`

Response:

`HTTP 201`

Returnerer den oprettede `MessageDTO`.

## Edge cases

- Hvis `customerId` ikke er et gyldigt UUID, returneres HTTP 400.
- Hvis kunden ikke findes, returneres HTTP 404.
- Hvis den valgte bruger ikke er en kunde, returneres HTTP 400.
- Hvis der endnu ikke findes beskeder mellem kunden og admin, returnerer GET-endpointet en tom liste.

## API response og edge cases

DTO'er returneres direkte som JSON-objekter.

Lister returneres direkte som JSON-arrays uden wrapper-object.

Thread-oversigten indeholder kun kunder, der har mindst én besked.

Hvis en eksisterende kunde endnu ikke har nogen beskeder:

- `GET /api/messages/threads/{customerId}` returnerer `HTTP 200` med `[]`
- `PATCH /api/messages/threads/{customerId}/read` returnerer `HTTP 204`

Hvis `customerId` tilhører en bruger, der ikke opfylder definitionen på en kunde, returneres:

`HTTP 400`

GET-endpoints må ikke ændre `read`-status.

## Domænebeslutninger

Systemet forventer præcis én `User` med `Role.ADMIN`, som fungerer som chat-admin.


Admin skal have mulighed for at starte en besked til en kunde

## Request-felter

Requests må kun indeholde de felter, der er dokumenteret for endpointet.

Ukendte felter og server-controlled felter skal afvises med HTTP 400.

Server-controlled felter omfatter blandt andet:

- `id`
- `senderId`
- `recipientId`
- `createdAt`
- `read`

## Roller og chat-admin

Chatten bruger præcis én `User` med `Role.ADMIN` som chat-admin.

Eksisterende `Role.OWNER`-authorization må ikke ændres.

Selvom OWNER kan få adgang til ADMIN-routes gennem eksisterende security-logik, må OWNER ikke automatisk bruges som `sender`, `recipient` eller chat-admin i stedet for den ene `Role.ADMIN`.

Hvis systemet finder 0 eller mere end 1 bruger med `Role.ADMIN`, skal operationen fejle kontrolleret og må ikke vælge en tilfældig admin.

## Read-adfærd

GET-endpoints må ikke ændre read-status.

Read-status ændres kun via det eksplicitte PATCH-endpoint.

`PATCH /api/messages/threads/{customerId}/read`

markerer kun beskeder fra kunden til authenticated admin som læst.

Markering af admin-beskeder som læst fra kundesiden er uden for scope for denne ticket.

## Afhængighed til chat-notifikationer

Denne ticket implementerer ikke SMTP eller email-notifikationer.

Chat-featuret skal dog være kompatibelt med ticket 5, som senere skal kunne sende email-notifikation ved nye chatbeskeder.

Email-notifikationer skal respektere brugerens eksisterende `emailNotifications`-preference.

Der må ikke tilføjes ny email- eller event-arkitektur som del af denne ticket.

## MessageController

Opret:

`backend/src/main/java/app/controllers/MessageController.java`

Controlleren er ansvarlig for HTTP-laget for chat-featuret.

Den skal:

- registrere featurets endpoints
- læse request body, path params og query params
- hente authenticated user fra den eksisterende authentication context
- kalde `ConversationService`
- returnere korrekte HTTP-statuskoder og JSON-responses

Controlleren må ikke tilgå `EntityManager` eller `MessageDAO` direkte.

Business logic skal ligge i `ConversationService`.

## ConversationService

Opret:

`backend/src/main/java/app/services/ConversationService.java`

`ConversationService` indeholder business logic for chat-featuret.

Servicen skal håndtere:

- oprettelse af kundebeskeder
- oprettelse af admin-svar
- hentning af kundens egen samtale
- hentning af adminens thread-oversigt
- hentning af én kundethread
- read/unread-logik
- validering af chat-relateret input
- adgangskontrol omkring hvilke beskeder en kunde må se
- gruppering af `Message`-entiteter til threads
- oprettelse af ny samtale fra admin til kunde
- afgøre om en admin-besked starter en ny samtale eller er et svar i en eksisterende samtale
- kræve `subject` ved første admin-besked
- afvise `subject` i en eksisterende samtale

Servicen skal bruge `MessageDAO` til persistence.

Servicen må ikke håndtere HTTP direkte.

## MessageDAO

Opret:

`backend/src/main/java/app/daos/MessageDAO.java`

`MessageDAO` er ansvarlig for persistence og queries for `Message`.

DAO'en skal bygge videre på eksisterende `EntityManagerDAO`.

Den skal understøtte de queries, som `ConversationService` har brug for, herunder:

- gemme en message
- hente messages mellem en kunde og admin
- hente messages kronologisk
- hente data til adminens thread-oversigt
- finde ulæste messages
- markere relevante messages som læst
- understøtte sortering efter navn, virksomhed, emne og dato

Brug JPQL eller repositoryets eksisterende query-pattern.

DAO'en må ikke indeholde HTTP-logik.

## ThreadDTO

Opret:

`backend/src/main/java/app/dtos/ThreadDTO.java`

`ThreadDTO` er en read-model til adminens samtaleoversigt.

Den repræsenterer ikke en database-entitet og må ikke annoteres med JPA.

Den skal indeholde de oplysninger, adminens venstre samtaleliste har brug for:

- `customerId`
- `customerName`
- `company`
- `subject`
- `lastMessage`
- `lastMessageAt`
- `unread`

Eksempel:

```json
{
  "customerId": "uuid",
  "customerName": "Peter Jensen",
  "company": "Peters Keramik",
  "subject": "Spørgsmål til stand",
  "lastMessage": "Hej, jeg ville lige høre...",
  "lastMessageAt": "2026-10-06T14:21:32",
  "unread": true
}
```

## MessageDTO

Opret:

`backend/src/main/java/app/dtos/MessageDTO.java`

`MessageDTO` repræsenterer én besked i API'et.

Den skal mindst indeholde:

- `id`
- `senderId`
- `recipientId`
- `subject`
- `body`
- `createdAt`
- `read`

JPA-entiteter som `User` må ikke eksponeres direkte i API-responsen.

## MessageMapper

Opret:

`backend/src/main/java/app/mappers/MessageMapper.java`

`MessageMapper` skal mappe eksplicit mellem:

- `Message`
- `MessageDTO`

Følg repositoryets eksisterende mapper-pattern.

`ThreadDTO` behøver ikke en separat mapper, da den er en read-model sammensat af `Message`- og `User`-data.

## Read / unread

`read` angiver, om beskeden er blevet læst af dens recipient.

Nye beskeder oprettes altid med:

`read = false`

En admin-thread har:

`unread = true`

hvis mindst én besked fra kunden til admin stadig har `read = false`.

En besked sendt fra admin til kunden må ikke i sig selv gøre threaden unread for admin.

Hvis threaden allerede har ulæste kundebeskeder, skal `unread = true` bevares, selv om admin sender en ny besked.

Når admin markerer en thread som læst, ændres kun kundens ulæste beskeder til admin.

## Validering

Ugyldigt input skal give HTTP 400 via eksisterende `ApiException`.

Krav:

### subject
- required ved ny kundebesked
- må ikke være blank
- max 200 tegn

### body
- required
- må ikke være blank
- max 5000 tegn

### customerId
- skal være gyldigt UUID

### sort
- kun `name`, `company`, `subject` og `date` accepteres

Malformed JSON og forkerte datatyper skal håndteres efter projektets eksisterende pattern.

## Chat-admin og OWNER

Admin-operationer kræver, at den authenticated bruger er systemets ene chat-admin med `Role.ADMIN`.

Andre brugere, herunder en bruger med `Role.OWNER` uden `Role.ADMIN`, må ikke fungere som chat-admin.

Hvis authenticated bruger ikke er den ene chat-admin, returneres:

`HTTP 403`

via `ApiException` i `ConversationService`.

Den eksisterende security-arkitektur må ikke ændres.

## Chat-admin konfiguration

Systemet kræver præcis én bruger med `Role.ADMIN`.

Hvis der findes:

- 0 brugere med `Role.ADMIN`
- flere end 1 bruger med `Role.ADMIN`

returneres:

`HTTP 500`

via `ApiException`.

Ingen beskeder må oprettes, ændres eller markeres som læst i denne situation.

## Integration

Feature-agenten må ikke ændre:

- `Routing.java`
- `EntityRegistry.java`
- `UserDAO.java`

Integrationsansvarlig skal efter feature-merge:

- registrere `MessageController` i `Routing.java`
- registrere `Message.class` i `EntityRegistry.java`
- beslutte og implementere message-retention/cleanup ved sletning af en bruger

Routing, entity registration og deletion-handling skal være gennemført, før den integrerede user story betragtes som færdig.

## Tests

Opret:

- `MessageDAOTest`
- `ConversationServiceTest`
- `MessageControllerTest`
- `MessageTestSupport`

Brug isoleret H2.

Test mindst:

- kunde kan sende besked
- kunde kan hente egen samtale
- kunde får aldrig andre kunders beskeder
- admin kan hente thread-oversigt
- admin kan åbne thread
- admin kan svare
- unread/read virker
- sortering virker for alle fire værdier
- ugyldigt input giver 400
- relevante role-restriktioner håndhæves

Test også:

- OWNER uden ADMIN afvises fra admin-chat-operationer med 403
- 0 ADMIN-brugere giver 500
- flere ADMIN-brugere giver 500
- ukendte request-felter giver 400
- server-controlled request-felter giver 400
- GET-endpoints ændrer ikke read-status
- thread-oversigten indeholder kun kunder med mindst én besked
- eksisterende kunde uden beskeder giver `[]` på GET
- eksisterende kunde uden beskeder giver 204 på PATCH
- bruger der ikke er kunde giver 400
- admin kan starte en ny samtale med gyldigt `subject` og `body`
- manglende `subject` ved første admin-besked giver 400
- blankt `subject` ved første admin-besked giver 400
- `subject` over 200 tegn ved første admin-besked giver 400
- `subject` sendt i en eksisterende samtale giver 400
- kunden kan hente en samtale, som admin har startet
- en admin-besked opretter ikke unread-status, hvis der ikke allerede findes ulæste kundebeskeder
- hvis der allerede findes ulæste kundebeskeder, forbliver threaden `unread = true`, efter admin sender en besked
- OWNER afvises som kunde, hvis requesten når `ConversationService`
- en bruger med både `Role.USER` og `Role.ADMIN` afvises som kunde, hvis requesten når `ConversationService`

## Definition of Done

Opgaven er færdig, når:

- `Message`-entiteten er implementeret med relationer til `User` som sender og recipient.
- `MessageController`, `ConversationService`, `MessageDAO`, `MessageDTO`, `ThreadDTO` og `MessageMapper` er implementeret.
- Kunden kan sende en besked til admin.
- Kunden kan hente sin egen samtale og kan ikke få adgang til andre kunders beskeder.
- Admin kan hente en thread-oversigt med én thread pr. kunde.
- Admin kan åbne en kundes thread og se alle beskeder kronologisk.
- Admin kan svare kunden.
- Read/unread-status fungerer efter ticketens regler.
- Admin kan sortere thread-oversigten efter navn, virksomhed, emne og dato.
- Ugyldigt input håndteres med relevante `ApiException`-fejl.
- Authentication og authorization bruger projektets eksisterende security-pattern.
- Controlleren tilgår ikke databasen direkte.
- `ConversationService` indeholder featurets business logic.
- `MessageDAO` håndterer persistence og nødvendige queries.
- Mapping mellem `Message` og `MessageDTO` sker gennem `MessageMapper`.
- Der er ikke oprettet en `Thread`- eller `Conversation`-entity.
- Eksisterende `MessageService` er ikke ændret.
- Der er ikke lavet unrelated refactoring eller ændringer uden for ticketens scope.
- Relevante DAO-, service- og controller-tests består.
- Hele backend-test-suiten består, medmindre en eksisterende unrelated test allerede er defekt.
- Admin kan starte en ny samtale med en kunde.
- Første admin-besked kræver gyldigt `subject` og `body`.
- Admin kan svare i en eksisterende samtale uden at sende et nyt `subject`.
- Kunden kan hente en samtale, som admin har startet.
- Adminens egne udgående beskeder opretter ikke unread-status.
- Eksisterende unread-status bevares, hvis der stadig findes ulæste beskeder fra kunden til admin.

## Kontrol

Udfør følgende kontrol før opgaven erklæres færdig:

1. Kontrollér at implementationen følger `AGENTS.md`, `ARCHITECTURE.md` og `CODESTANDARD.md`.

2. Kør de relevante feature-tests:
   - `MessageDAOTest`
   - `ConversationServiceTest`
   - `MessageControllerTest`

3. Kør hele backend-test-suiten med Maven.

4. Verificér mindst følgende flows:
   - USER kan sende en besked til admin.
   - USER kan hente sin egen samtale.
   - `/api/messages/mine` bestemmer altid kunden fra authentication context og kan ikke bruges til at vælge en anden kunde
   - ADMIN kan hente thread-oversigten.
   - ADMIN kan åbne en kundethread.
   - ADMIN kan svare kunden.
   - Nye beskeder oprettes som ulæste.
   - Markering som læst ændrer kun relevante beskeder.
   - Sortering efter `name`, `company`, `subject` og `date` virker.
   - Ugyldigt input giver forventet 400-response.
   - Role-beskyttede endpoints kan ikke bruges af forkerte roller.
   - ADMIN kan starte en ny samtale med en kunde.
   - Første admin-besked kræver gyldigt `subject`.
   - `subject` afvises i en eksisterende samtale.
   - Kunden kan hente en admin-initieret samtale.
   - verificér at adminens udgående beskeder ikke opretter unread-status
   - verificér at eksisterende unread-status ikke ryddes, hvis ulæste kundebeskeder stadig findes

5. Kontrollér at API-responses bruger DTO'er og ikke eksponerer JPA-entiteter direkte.

6. Kontrollér at `MessageController` ikke bruger `EntityManager` eller `MessageDAO` direkte.

7. Kontrollér at der ikke er oprettet en `Thread`- eller `Conversation`-entity.

8. Kontrollér `git diff` og bekræft at:
   - kun filer inden for ticketens scope er ændret
   - `MessageService` er urørt
   - der ikke er lavet unrelated refactoring
   - genererede filer under `target/**` ikke er committed

9. Hvis integration ikke er en del af feature-agentens scope, kontrollér at:
   - `Routing.java` ikke er ændret
   - `EntityRegistry.java` ikke er ændret
   - nødvendige integrationsændringer er rapporteret tydeligt til integrationsansvarlig

10. Afslut med en kort rapport med:
   - oprettede/ændrede filer
   - hvilke tests der blev kørt
   - testresultater
   - eventuelle blockers eller antagelser
   - eventuelle nødvendige integrationsændringer

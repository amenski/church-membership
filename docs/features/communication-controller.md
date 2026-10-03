# CommunicationController

`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`

REST API under `/api/communications` to create, email and track announcements to members; VOLUNTEER+ reads, STAFF+ writes. SMTP config and the delivery-tracking model: [../email.md](../email.md).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/communications` | VOLUNTEER+ | - | `Communication[]` (`:67-72`) |
| GET | `/api/communications/{id}` | VOLUNTEER+ | `id` > 0 | `Communication`, or empty 404 (`:74-81`) |
| POST | `/api/communications` | STAFF+ | `SendCommunicationRequest` body | saved `Communication`, nothing sent (`:83-88`) |
| POST | `/api/communications/send-to-all` | STAFF+ | `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON; deliveries are stored with PENDING status, read them with `GET /{id}/deliveries`) (`:90-95`) |
| POST | `/api/communications/send-to-overdue/{months}` | STAFF+ | `months` >= 1, `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON, see send-to-all) (`:97-112`) |
| POST | `/api/communications/send-to-member/{memberId}` | STAFF+ | `memberId` > 0, `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON, one delivery stored) (`:114-130`) |
| GET | `/api/communications/{id}/deliveries` | VOLUNTEER+ | `id` > 0 | `MessageDelivery[]` (`:141-147`) |
| POST | `/api/communications/{id}/deliveries/{deliveryId}/retry` | STAFF+ | both ids > 0 | `MessageDelivery` (`:149-155`) |

Paths below are relative to `src/main/java/io/github/membertracker/` unless prefixed `docs`/`frontend`.

Request body is `SendCommunicationRequest` (`infrastructure/dto/SendCommunicationRequest.java:10-19`), shared with the frontend test as `src/test/resources/contracts/send-communication-request.json` (checked by `CommunicationContractTest`): `title` (required, max 200), `messageContent` (required, max 5000), `type` (`ANNOUNCEMENT`, `REMINDER`, `PERSONAL`; absent means `ANNOUNCEMENT`). The controller builds a fresh `Communication` from these three fields (`CommunicationController.java:133-139`), so a client cannot set `sentDate`, `createdDate`, `deliveries` or `sentToAllMembers`.

## Flows
| Flow | Audience | Sends email | Thread |
|------|----------|-------------|--------|
| create | none | no. Sets `createdDate` only (`usecase/CreateCommunicationUseCase.java:16-19`) | request |
| send-to-all | active members only, `findByActive(true)` (`usecase/SendCommunicationToAllMembersUseCase.java:50`) | yes, sets `sentToAllMembers` (`:47`) | save on request; emails on background thread (`:64`, `:72-115`) |
| send-to-overdue | members with `consecutiveMonthsMissed >= months` (`usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), channel fixed to EMAIL | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java:57-61`, `:75-103`) |
| send-to-member | the single member loaded by id (`CommunicationController.java:121-122`); unknown id -> 400 | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java:57-61`, `:75-103`) |
| retry | one delivery | yes, once | request thread, blocks until the SMTP attempts finish (`usecase/RetryDeliveryUseCase.java:58-63`) |

- Send flows: build one `PENDING` `MessageDelivery` per recipient, save the communication with them (`CommunicationDbRepository.save` writes the deliveries through the JPA cascade and returns them with their ids, `infrastructure/persistence/repository/CommunicationDbRepository.java:61-86`), return immediately. One background task per call (cached thread pool) then loops recipients with a 100 ms pause (`SendCommunicationToAllMembersUseCase.java:104`, `SendCommunicationToMembersUseCase.java:92`) and saves only the one delivery row after each status change via `MessageDeliveryRepository.save` (`SendCommunicationToAllMembersUseCase.java:117-136`, `SendCommunicationToMembersUseCase.java:105-124`); a failed row save is logged and the loop continues.
- Title and message are personalised per recipient at send time: `{{member_name}}` becomes the member's name (`MessageTemplates.personalize`, called from `SendCommunicationToAllMembersUseCase.java:79-80`, `SendCommunicationToMembersUseCase.java:81-82`, `RetryDeliveryUseCase.java:60-61`); the stored `Communication` keeps the placeholder. See [../email.md](../email.md#personalisation).
- The send response does not include the deliveries: `Communication.getDeliveries()` is `@JsonIgnore` (`domain/model/Communication.java:105-106`) because a delivery points back to its communication and the list would repeat every recipient's personal data. Read statuses with `GET /{id}/deliveries`.
- Retry: only `FAILED` (`RetryDeliveryUseCase.java:47`) and `EMAIL` (`:50`) deliveries. Success sets `SENT` + `deliveryTime` (`:65-68`); failure keeps `FAILED`, writes "Retry failed at ..." to `responseNotes` (`:70-71`).

## Collaborators
- Use cases: `usecase/GetAllCommunicationsUseCase.java`, `usecase/GetCommunicationByIdUseCase.java`, `usecase/GetMemberByIdUseCase.java` (send-to-member), `usecase/CreateCommunicationUseCase.java`, `usecase/SendCommunicationToAllMembersUseCase.java`, `usecase/SendCommunicationToMembersUseCase.java`, `usecase/GetMembersWithMissedPaymentsUseCase.java`, `usecase/GetDeliveriesByCommunicationUseCase.java`, `usecase/RetryDeliveryUseCase.java` 
- `infrastructure/service/EmailService.java:71` `sendSimpleEmailWithRetry` (exponential backoff, attempts from mail properties `:78`); `sendSimpleEmail` delegates to it (`:64-66`), so both send flows retry
- Models: `Communication`, `MessageDelivery` (`domain/model/MessageDelivery.java:15-21`: status `PENDING|SENT|FAILED|DELIVERED`, channel `EMAIL|SMS|WHATSAPP`)
- Hierarchy and roles: [../authentication.md](../authentication.md)

## Errors
Format: [../architecture.md](../architecture.md).

| Status | Source |
|--------|--------|
| 400 | `@Valid` body failure, `errors[{field,message}]` (`infrastructure/handler/GlobalExceptionHandler.java:52-65`) |
| 400 | `@Positive` / `@Min(1)` path failure (`GlobalExceptionHandler.java:75-84`) |
| 400 | any `CommunicationDomainException`, with `code` (`GlobalExceptionHandler.java:67-73`); codes `COMMUNICATION_001`-`005` (`domain/exception/CommunicationDomainException.java:9-13`): delivery not found, delivery/communication mismatch, not retryable, communication not found, already sent |
| 403 | role too low (`GlobalExceptionHandler.java:86-91`) |
| 401 | not authenticated (`GlobalExceptionHandler.java:93-99`) |
| 404 | `GET /{id}` with unknown id only, empty body (`CommunicationController.java:74`) |
| 500 | anything else (`GlobalExceptionHandler.java:101-106`) |

## Side effects
- Emails via SMTP; disabled mail returns `false` immediately, so deliveries become `FAILED` (`EmailService.java:73-76`). See [../email.md](../email.md).
- Rows in communications and message deliveries tables; background thread keeps writing after the response.
- SMS/WhatsApp branch of the shared use case marks deliveries `FAILED` ("not implemented") (`SendCommunicationToMembersUseCase.java:62-69`); the controller only ever passes EMAIL (`CommunicationController.java:109`, `:127`).

## Gotchas
- Not-found is a 400, not 404, for retry (`RetryDeliveryUseCase.java:40-41` throws a domain exception; `GlobalExceptionHandler.java:70`).
- `GET /{id}` unknown id returns an empty 404, not a ProblemDetail (`CommunicationController.java:74`).
- `GET /{id}/deliveries` unknown id returns 200 with `[]` (`usecase/GetDeliveriesByCommunicationUseCase.java:36-40`).
- A failed retry returns 200 with `status: FAILED`; check the body.
- send-to-member with an unknown `memberId` is a 400 `MEMBER_004`, not 404 (`CommunicationController.java:121-122`).
- `CommunicationDbRepository.save` writes the deliveries of the communication it is given; `findAll`/`findById` never load them, and saving a communication that has no deliveries (for example one read back with `findById`) leaves the stored delivery rows alone (no orphan removal). Covered by `CommunicationDeliveryPersistenceTest`.
- `create` leaves `sentDate` null and sends nothing; the three send endpoints each build a new communication from the request body, so no endpoint sends an existing communication by id.
- A client-supplied `sentDate` in the body is ignored, not rejected: the request DTO has no such field.
- send-to-overdue has no `active` filter (`infrastructure/persistence/repository/MemberJpaRepository.java:16`), unlike send-to-all; an empty match still saves a communication marked sent with zero deliveries.

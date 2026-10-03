# CommunicationController

`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`

REST API under `/api/communications` to create, email and track announcements to members; VOLUNTEER+ reads, STAFF+ writes. SMTP config and the delivery-tracking model: [../email.md](../email.md).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/communications` | VOLUNTEER+ | - | `Communication[]` (`:67-72`) |
| GET | `/api/communications/{id}` | VOLUNTEER+ | `id` > 0 | `Communication`, or empty 404 (`:74-81`) |
| POST | `/api/communications` | STAFF+ | `SendCommunicationRequest` body | saved `Communication`, nothing sent (`:83-88`) |
| POST | `/api/communications/send-to-all` | STAFF+ | `SendCommunicationRequest` body | saved `Communication` with PENDING deliveries (`:90-95`) |
| POST | `/api/communications/send-to-overdue/{months}` | STAFF+ | `months` >= 1, `SendCommunicationRequest` body | saved `Communication` with PENDING deliveries (`:97-112`) |
| POST | `/api/communications/send-to-member/{memberId}` | STAFF+ | `memberId` > 0, `SendCommunicationRequest` body | saved `Communication` with one PENDING delivery (`:114-130`) |
| GET | `/api/communications/{id}/deliveries` | VOLUNTEER+ | `id` > 0 | `MessageDelivery[]` (`:141-147`) |
| POST | `/api/communications/{id}/deliveries/{deliveryId}/retry` | STAFF+ | both ids > 0 | `MessageDelivery` (`:149-155`) |

Paths below are relative to `src/main/java/io/github/membertracker/` unless prefixed `docs`/`frontend`.

Request body is `SendCommunicationRequest` (`infrastructure/dto/SendCommunicationRequest.java:10-19`), shared with the frontend test as `src/test/resources/contracts/send-communication-request.json` (checked by `CommunicationContractTest`): `title` (required, max 200), `messageContent` (required, max 5000), `type` (`ANNOUNCEMENT`, `REMINDER`, `PERSONAL`; absent means `ANNOUNCEMENT`). The controller builds a fresh `Communication` from these three fields (`CommunicationController.java:133-139`), so a client cannot set `sentDate`, `createdDate`, `deliveries` or `sentToAllMembers`.

## Flows
| Flow | Audience | Sends email | Thread |
|------|----------|-------------|--------|
| create | none | no. Sets `createdDate` only (`usecase/CreateCommunicationUseCase.java:16-19`) | request |
| send-to-all | active members only, `findByActive(true)` (`usecase/SendCommunicationToAllMembersUseCase.java:45`) | yes, sets `sentToAllMembers` (`:42`) | save on request; emails on background thread (`:62`, `:67-110`) |
| send-to-overdue | members with `consecutiveMonthsMissed >= months` (`usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), channel fixed to EMAIL | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java:55-56`, `:70-98`) |
| send-to-member | the single member loaded by id (`CommunicationController.java:121-122`); unknown id -> 400 | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java:55-56`, `:70-98`) |
| retry | one delivery | yes, once | request thread, blocks until the SMTP attempts finish (`usecase/RetryDeliveryUseCase.java:57-62`) |

- Send flows: build one `PENDING` `MessageDelivery` per recipient, save, return immediately. One background task per call (cached thread pool) then loops recipients with a 100 ms pause (`SendCommunicationToAllMembersUseCase.java:99`, `SendCommunicationToMembersUseCase.java:87`) and saves the whole communication after each status change (`SendCommunicationToAllMembersUseCase.java:112-127`).
- Response therefore shows `PENDING`; read final status via the deliveries endpoint.
- Retry: only `FAILED` (`RetryDeliveryUseCase.java:46`) and `EMAIL` (`:49`) deliveries. Success sets `SENT` + `deliveryTime` (`:64-67`); failure keeps `FAILED`, writes "Retry failed at ..." to `responseNotes` (`:69-70`).

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
- SMS/WhatsApp branch of the shared use case marks deliveries `FAILED` ("not implemented") (`SendCommunicationToMembersUseCase.java:57-64`); the controller only ever passes EMAIL (`CommunicationController.java:109`, `:127`).

## Gotchas
- Not-found is a 400, not 404, for retry (`RetryDeliveryUseCase.java:39-40` throws a domain exception; `GlobalExceptionHandler.java:70`).
- `GET /{id}` unknown id returns an empty 404, not a ProblemDetail (`CommunicationController.java:74`).
- `GET /{id}/deliveries` unknown id returns 200 with `[]` (`usecase/GetDeliveriesByCommunicationUseCase.java:36-40`).
- A failed retry returns 200 with `status: FAILED`; check the body.
- send-to-member with an unknown `memberId` is a 400 `MEMBER_004`, not 404 (`CommunicationController.java:121-122`).
- `create` leaves `sentDate` null and sends nothing; the three send endpoints each build a new communication from the request body, so no endpoint sends an existing communication by id.
- A client-supplied `sentDate` in the body is ignored, not rejected: the request DTO has no such field.
- send-to-overdue has no `active` filter (`infrastructure/persistence/repository/MemberJpaRepository.java:16`), unlike send-to-all; an empty match still saves a communication marked sent with zero deliveries.

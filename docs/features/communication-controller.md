# CommunicationController

`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`

REST API under `/api/communications` to create, email and track announcements to members; VOLUNTEER+ reads, STAFF+ writes. SMTP config and the delivery-tracking model: [../email.md](../email.md).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/communications` | VOLUNTEER+ | - | `Communication[]` (`:61-66`) |
| GET | `/api/communications/{id}` | VOLUNTEER+ | `id` > 0 | `Communication`, or empty 404 (`:68-75`) |
| POST | `/api/communications` | STAFF+ | `Communication` body | saved `Communication`, nothing sent (`:77-82`) |
| POST | `/api/communications/send-to-all` | STAFF+ | `Communication` body | saved `Communication` with PENDING deliveries (`:84-89`) |
| POST | `/api/communications/send-to-overdue/{months}` | STAFF+ | `months` >= 1, `Communication` body | saved `Communication` with PENDING deliveries (`:91-106`) |
| GET | `/api/communications/{id}/deliveries` | VOLUNTEER+ | `id` > 0 | `MessageDelivery[]` (`:108-114`) |
| POST | `/api/communications/{id}/deliveries/{deliveryId}/retry` | STAFF+ | both ids > 0 | `MessageDelivery` (`:116-122`) |

Paths below are relative to `src/main/java/io/github/membertracker/` unless prefixed `docs`/`frontend`.

Request body is the domain `Communication` itself, no DTO. Client-relevant fields: `title` (required, max 200), `messageContent` (required, max 5000), `type` (`ANNOUNCEMENT`, `REMINDER`, `PERSONAL`) (`domain/model/Communication.java:24-35`, `domain/enumeration/CommunicationType.java:3-5`).

## Flows
| Flow | Audience | Sends email | Thread |
|------|----------|-------------|--------|
| create | none | no. Sets `createdDate` only (`usecase/CreateCommunicationUseCase.java:16-19`) | request |
| send-to-all | active members only, `findByActive(true)` (`usecase/SendCommunicationToAllMembersUseCase.java:45`) | yes, sets `sentToAllMembers` (`:42`) | save on request; emails on background thread (`:62`, `:67-110`) |
| send-to-overdue | members with `consecutiveMonthsMissed >= months` (`usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), channel fixed to EMAIL | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java:55-56`, `:70-98`) |
| retry | one delivery | yes, once | request thread, blocks until the SMTP attempts finish (`usecase/RetryDeliveryUseCase.java:57-62`) |

- Send flows: build one `PENDING` `MessageDelivery` per recipient, save, return immediately. One background task per call (cached thread pool) then loops recipients with a 100 ms pause (`SendCommunicationToAllMembersUseCase.java:99`, `SendCommunicationToMembersUseCase.java:87`) and saves the whole communication after each status change (`SendCommunicationToAllMembersUseCase.java:112-127`).
- Response therefore shows `PENDING`; read final status via the deliveries endpoint.
- Retry: only `FAILED` (`RetryDeliveryUseCase.java:46`) and `EMAIL` (`:49`) deliveries. Success sets `SENT` + `deliveryTime` (`:64-67`); failure keeps `FAILED`, writes "Retry failed at ..." to `responseNotes` (`:69-70`).

## Collaborators
- Use cases: `usecase/GetAllCommunicationsUseCase.java`, `usecase/GetCommunicationByIdUseCase.java`, `usecase/CreateCommunicationUseCase.java`, `usecase/SendCommunicationToAllMembersUseCase.java`, `usecase/SendCommunicationToMembersUseCase.java`, `usecase/GetMembersWithMissedPaymentsUseCase.java`, `usecase/GetDeliveriesByCommunicationUseCase.java`, `usecase/RetryDeliveryUseCase.java` 
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
- SMS/WhatsApp branch of the shared use case marks deliveries `FAILED` ("not implemented") (`SendCommunicationToMembersUseCase.java:57-64`); the controller only ever passes EMAIL (`CommunicationController.java:103`).

## Gotchas
- Not-found is a 400, not 404, for retry (`RetryDeliveryUseCase.java:39-40` throws a domain exception; `GlobalExceptionHandler.java:70`).
- `GET /{id}` unknown id returns an empty 404, not a ProblemDetail (`CommunicationController.java:74`).
- `GET /{id}/deliveries` unknown id returns 200 with `[]` (`usecase/GetDeliveriesByCommunicationUseCase.java:36-40`).
- A failed retry returns 200 with `status: FAILED`; check the body.
- A body with `sentDate` set is rejected 400 `COMMUNICATION_005` by both send flows (`Communication.java:116-122`).
- `create` leaves `sentDate` null but no endpoint sends an existing communication by id; both send endpoints take a new body.
- send-to-overdue has no `active` filter (`infrastructure/persistence/repository/MemberJpaRepository.java:16`), unlike send-to-all; an empty match still saves a communication marked sent with zero deliveries.

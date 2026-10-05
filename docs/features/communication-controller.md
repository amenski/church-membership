# CommunicationController

`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`

REST API under `/api/communications` to email and track announcements to members; VOLUNTEER+ reads, STAFF+ writes. SMTP config and the delivery-tracking model: [../email.md](../email.md).

## Endpoints
| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/communications` | VOLUNTEER+ | - | `Communication[]`, each with `recipientCount` and `deliverySummary` (see below) |
| GET | `/api/communications/{id}` | VOLUNTEER+ | `id` > 0 | `Communication` (same two extra fields), or empty 404 |
| POST | `/api/communications/send-to-all` | STAFF+ | `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON; deliveries are stored with PENDING status, read them with `GET /{id}/deliveries`) |
| POST | `/api/communications/send-to-overdue/{months}` | STAFF+ | `months` >= 1, `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON, see send-to-all) |
| POST | `/api/communications/send-to-member/{memberId}` | STAFF+ | `memberId` > 0, `SendCommunicationRequest` body | saved `Communication` (no `deliveries` in the JSON, one delivery stored) |
| GET | `/api/communications/{id}/deliveries` | VOLUNTEER+ | `id` > 0 | `MessageDelivery[]`, each with `attempts` (send attempts made, see below) |
| POST | `/api/communications/{id}/deliveries/{deliveryId}/retry` | STAFF+ | both ids > 0 | `MessageDelivery` |

Paths below are relative to `src/main/java/io/github/membertracker/` unless prefixed `docs`/`frontend`.

Request body is `SendCommunicationRequest` (`infrastructure/dto/SendCommunicationRequest.java`), shared with the frontend test as `src/test/resources/contracts/send-communication-request.json` (checked by `CommunicationContractTest`): `title` (required, max 200), `messageContent` (required, max 5000), `type` (`ANNOUNCEMENT`, `REMINDER`, `PERSONAL`; absent means `ANNOUNCEMENT`). The controller builds a fresh `Communication` from these three fields (`CommunicationController.java`), so a client cannot set `sentDate`, `createdDate`, `deliveries` or `sentToAllMembers`.

## Flows
| Flow | Audience | Sends email | Thread |
|------|----------|-------------|--------|
| send-to-all | members with status MEMBER (`findDuesPaying()`), then `Recipients.reachable` (no email dropped, one per address); none -> 400 `COMMUNICATION_006`, nothing stored (`usecase/SendCommunicationToAllMembersUseCase.java`) | yes, sets `sentToAllMembers` | save on request; emails on background thread |
| send-to-overdue | MEMBER-status members with `consecutiveMonthsMissed >= months`, longest behind first (`GetMembersWithMissedPaymentsUseCase`); none -> 400 `COMMUNICATION_006`, nothing stored; channel fixed to EMAIL | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java`) |
| send-to-member | the single member loaded by id (`CommunicationController.java`); unknown id -> 400; no email -> 400 `COMMUNICATION_007` | yes | save on request; emails on background thread (`usecase/SendCommunicationToMembersUseCase.java`) |
| retry | one delivery | yes, once | request thread, blocks until the SMTP attempts finish (`usecase/RetryDeliveryUseCase.java`) |

- Empty audience: both send use cases throw `CommunicationDomainException.noRecipients()` (`COMMUNICATION_006`, "There is nobody to send this to.") BEFORE `markAsSent` or any save, so no communication or delivery row is created and no email goes out.
- List counts: `Communication` has two derived, non-persisted JSON properties, `recipientCount` (number of deliveries) and `deliverySummary` (`{sent, failed, pending, delivered}`, record `DeliverySummary`; all zero when there are no deliveries). `CommunicationDbRepository.findAll`/`findById` fill them with ONE grouped query for the whole list (`MessageDeliveryJpaRepository.countByCommunicationIdsGroupedByStatus`: count by communication and status for `communication.id in (:ids)`); `save` computes them from the deliveries it carries (or asks the database when it carries none). The list screen no longer needs to open each delivery dialog to show status; the dialog still uses `GET /{id}/deliveries`. Counts are a snapshot: while the background send runs, `pending` shrinks on each reload.
- Send flows: build one `PENDING` `MessageDelivery` per recipient, save the communication with them (`CommunicationDbRepository.save` writes the deliveries through the JPA cascade and returns them with their ids, `infrastructure/persistence/repository/CommunicationDbRepository.java`), return immediately. One background task per call (cached thread pool) then loops recipients with a 100 ms pause (`SendCommunicationToAllMembersUseCase.java`, `SendCommunicationToMembersUseCase.java`) and saves only the one delivery row after each status change via `MessageDeliveryRepository.save` (`SendCommunicationToAllMembersUseCase.java`, `SendCommunicationToMembersUseCase.java`); a failed row save is logged and the loop continues.
- Title and message are personalised per recipient at send time: `{{member_name}}` becomes the member's name (`MessageTemplates.personalize`, called from `SendCommunicationToAllMembersUseCase.java`, `SendCommunicationToMembersUseCase.java`, `RetryDeliveryUseCase.java`); the stored `Communication` keeps the placeholder. See [../email.md](../email.md#personalisation).
- The send response does not include the deliveries: `Communication.getDeliveries()` is `@JsonIgnore` (`domain/model/Communication.java`) because a delivery points back to its communication and the list would repeat every recipient's personal data. Read statuses with `GET /{id}/deliveries`.
- Attempts: every delivery carries `attempts` (int, default 0). The send use cases count the tries through the `EmailService.RetryCallback` and store the number with the status (`SendCommunicationToAllMembersUseCase.java`; `SendCommunicationToMembersUseCase.java`); it stays 0 when no try was made (mail disabled, SMS/WhatsApp stubs). A retry adds its own attempts to the stored count (`RetryDeliveryUseCase.java`). Persisted in `message_delivery.attempts` (migration 008), mapped in both delivery mappers (`MessageDeliveryDbRepository.java`, `CommunicationDbRepository.java`).
- Retry: only `FAILED` (`RetryDeliveryUseCase.java`) and `EMAIL` deliveries. Success sets `SENT` + `deliveryTime`; failure keeps `FAILED`, writes "Retry failed at ..." to `responseNotes`.

## Collaborators
- Use cases: `usecase/GetAllCommunicationsUseCase.java`, `usecase/GetCommunicationByIdUseCase.java`, `usecase/GetMemberByIdUseCase.java` (send-to-member), `usecase/SendCommunicationToAllMembersUseCase.java`, `usecase/SendCommunicationToMembersUseCase.java`, `usecase/GetMembersWithMissedPaymentsUseCase.java`, `usecase/GetDeliveriesByCommunicationUseCase.java`, `usecase/RetryDeliveryUseCase.java` 
- `infrastructure/service/EmailService.java` `sendSimpleEmailWithRetry` (exponential backoff, attempts from mail properties); `sendSimpleEmail` delegates to it; both send use cases and the retry use case call `sendSimpleEmailWithRetry` directly, passing a callback that counts the attempts
- Models: `Communication`, `MessageDelivery` (`domain/model/MessageDelivery.java`: status `PENDING|SENT|FAILED|DELIVERED`, channel `EMAIL|SMS|WHATSAPP`)
- Hierarchy and roles: [../authentication.md](../authentication.md)

## Errors
Format: [../architecture.md](../architecture.md).

| Status | Source |
|--------|--------|
| 400 | `@Valid` body failure, `errors[{field,message}]` (`infrastructure/handler/GlobalExceptionHandler.java`) |
| 400 | `@Positive` / `@Min(1)` path failure (`GlobalExceptionHandler.java`) |
| 400 | any `CommunicationDomainException`, with `code` (`GlobalExceptionHandler.java`); codes `COMMUNICATION_001`-`008` (`domain/exception/CommunicationDomainException.java`): delivery not found, delivery/communication mismatch, not retryable, communication not found, already sent, no recipients, member has no email, member cannot receive messages (status other than MEMBER) |
| 403 | role too low (`GlobalExceptionHandler.java`) |
| 401 | not authenticated (`GlobalExceptionHandler.java`) |
| 404 | `GET /{id}` with unknown id only, empty body (`CommunicationController.java`) |
| 500 | anything else (`GlobalExceptionHandler.java`) |

## Side effects
- Emails via SMTP; disabled mail returns `false` immediately, so deliveries become `FAILED` (`EmailService.java`). See [../email.md](../email.md).
- Rows in communications and message deliveries tables; background thread keeps writing after the response.
- SMS/WhatsApp branch of the shared use case marks deliveries `FAILED` ("not implemented") (`SendCommunicationToMembersUseCase.java`); the controller only ever passes EMAIL (`CommunicationController.java`).

## Gotchas
- Not-found is a 400, not 404, for retry (`RetryDeliveryUseCase.java` throws a domain exception; `GlobalExceptionHandler.java`).
- `GET /{id}` unknown id returns an empty 404, not a ProblemDetail (`CommunicationController.java`).
- `GET /{id}/deliveries` unknown id returns 200 with `[]` (`usecase/GetDeliveriesByCommunicationUseCase.java`).
- A failed retry returns 200 with `status: FAILED`; check the body.
- send-to-member with an unknown `memberId` is a 400 `MEMBER_004`, not 404 (`CommunicationController.java`).
- `CommunicationDbRepository.save` writes the deliveries of the communication it is given; `findAll`/`findById` never load them, and saving a communication that has no deliveries (for example one read back with `findById`) leaves the stored delivery rows alone (no orphan removal). Covered by `CommunicationDeliveryPersistenceTest`.
- There is no draft endpoint: `POST /api/communications` was removed (the browser stopped using it when "Specific member" moved to send-to-member), so it answers 405. The three send endpoints each build a new communication from the request body; no endpoint sends an existing communication by id.
- A client-supplied `sentDate` in the body is ignored, not rejected: the request DTO has no such field.
- send-to-overdue reaches MEMBER-status members only (same as send-to-all); send-to-member sends to the member given only when their status is MEMBER and they have an email, else 400 `COMMUNICATION_008` or `COMMUNICATION_007`.

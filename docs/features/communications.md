# Communications

Emails to members: staff write a message and send it to everyone, to overdue members or to one member; everyone with access can see the message list and its delivery status.

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Communications screen | VOLUNTEER | `/communications` (`frontend/src/router/index.js:29-34`, guard `:101-105`) |
| View the message list | VOLUNTEER | `GET /api/communications` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:67-72`) |
| View one message's deliveries | VOLUNTEER | delivery dialog, `GET /api/communications/{id}/deliveries` (`CommunicationController.java:141-147`) |
| Send to all active members | STAFF | `POST /api/communications/send-to-all` (`CommunicationController.java:90-95`) |
| Send to members overdue by N months | STAFF | `POST /api/communications/send-to-overdue/{months}` (`CommunicationController.java:97-112`) |
| Send to one member | STAFF | `POST /api/communications/send-to-member/{memberId}` (`CommunicationController.java:114-130`) |
| Retry a failed delivery | STAFF | Retry button, `POST /api/communications/{id}/deliveries/{deliveryId}/retry` (`CommunicationController.java:149-155`) |

Role view of the screen:
- VOLUNTEER sees the list and the delivery dialog, without the compose form and without Retry buttons (`frontend/src/views/CommunicationsView.vue:6`, `:167`).
- STAFF and ADMIN see the compose form and Retry (`isStaff`, `frontend/src/stores/authStore.js:41`).
- MEMBER cannot open the screen: the route sends them to their profile with `?error=access_denied` (`frontend/src/router/index.js:102-104`, `authStore.js:43`); the API answers 403.

## How it works
### View the message list
1. The screen loads all members and all communications once on open and again after every send (`CommunicationsView.vue:236-247`, `:280`).
2. "Recent Communications" shows date sent, title, recipients, a delivery button and a View button (`CommunicationsView.vue:53-97`). The list is not sorted, paged or filtered by the screen.
3. Recipients reads "All Members" for a send-to-all message and "-" for everything else (`CommunicationsView.vue:372-381`).
4. The delivery button reads "Click to view" (`CommunicationsView.vue:351-359`).
5. On a load failure the list stays empty and nothing is shown to the user (`CommunicationsView.vue:244-246`).

### Compose a message (STAFF+)
1. Pick Recipients (All Members, Overdue Members, Specific Member). Overdue adds a "Months Overdue" number (default 1); Specific adds a member list (`CommunicationsView.vue:10-31`, `:223-229`).
2. Type Subject and Message. Both are required; a tip says `{{member_name}}` inserts each member's name (`CommunicationsView.vue:33-42`).
3. Send Message sends only `title` and `messageContent`, trimmed, always as type `ANNOUNCEMENT` (`frontend/src/utils/communicationPayload.js:2-7`).
4. While the request runs, the button is disabled and shows a spinner (`CommunicationsView.vue:44-47`, `:265`, `:296`).
5. Success: toast "Sending started", the list reloads and the form resets (`CommunicationsView.vue:274-287`). The toast means the server accepted the send, not that emails arrived.
6. Failure (blank title, unknown member, months below 1, 403): error toast with the server's message; the form keeps what was typed (`CommunicationsView.vue:288-295`, `frontend/src/services/api.js:81-88`).
7. Server limits: title up to 200 characters, message up to 5000 ([communication-controller.md](communication-controller.md)).

### Send to all active members (STAFF+)
1. Choose All Members and Send Message. A browser confirm asks "Send to N members?" where N counts active members in the loaded list; Cancel stops (`CommunicationsView.vue:259-263`, `:248-251`).
2. `POST /api/communications/send-to-all` (`CommunicationsView.vue:267-268`).
3. The server marks the message as sent and as sent to all members, and looks up the active members only (`src/main/java/io/github/membertracker/usecase/SendCommunicationToAllMembersUseCase.java:46-50`).
4. It prepares one PENDING delivery per member, saves the message together with its deliveries, and starts the background send (`SendCommunicationToAllMembersUseCase.java:53-67`). See "What happens after Send".

### Send to members overdue by N months (STAFF+)
1. Choose Overdue Members and enter N (`CommunicationsView.vue:28-31`). A browser confirm asks "Send to M members?" where M counts members with `consecutiveMonthsMissed >= N` in the loaded list (`CommunicationsView.vue:252-253`, `:259-263`).
2. `POST /api/communications/send-to-overdue/{N}`; N must be at least 1, otherwise 400 (`CommunicationController.java:100-101`).
3. The server selects members with `consecutiveMonthsMissed >= N`, active or not (`src/main/java/io/github/membertracker/usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), and sends by email (`CommunicationController.java:104-111`).
4. If nobody matches, the message is still saved and marked as sent, with no recipients (`src/main/java/io/github/membertracker/usecase/SendCommunicationToMembersUseCase.java:44-57`).

### Send to one member (STAFF+)
1. Choose Specific Member and pick the member; no confirmation is asked (`CommunicationsView.vue:19-26`, `:259`).
2. `POST /api/communications/send-to-member/{memberId}` (`CommunicationsView.vue:271-273`).
3. An unknown member id returns 400 (`CommunicationController.java:121-122`); otherwise one PENDING delivery is created and the send runs in the background (`CommunicationController.java:123-129`).
4. The Dashboard's Send Reminder button uses this same endpoint: [dashboard.md](dashboard.md).

### Inspect deliveries
1. Click the delivery button or View on a row (`CommunicationsView.vue:73-90`).
2. The dialog shows the title, sent date, three cards (Sent, Failed, Pending) and a table with recipient, email, status, delivered at, notes and actions (`CommunicationsView.vue:100-184`). Cards count `SENT`, `FAILED` and `PENDING` only (`CommunicationsView.vue:311-317`).
3. The dialog is a snapshot taken when it opens: PENDING rows do not change until it is closed and reopened (`CommunicationsView.vue:299-310`).
4. If the deliveries cannot be loaded, the dialog does not open and nothing is shown (`CommunicationsView.vue:306-309`).

### Retry a failed delivery (STAFF+)
1. In the dialog, a Retry button appears on rows with status FAILED (`CommunicationsView.vue:166-179`).
2. The server accepts only FAILED deliveries on the EMAIL channel that belong to that message (`src/main/java/io/github/membertracker/usecase/RetryDeliveryUseCase.java:43-52`).
3. It re-sends once and the request waits for the result (`RetryDeliveryUseCase.java:58-63`). The retry personalises the text for that member.
4. Success: status becomes SENT with a time, toast "Delivery re-sent". Failure: status stays FAILED, notes read "Retry failed at ...", toast "Retry failed" (`RetryDeliveryUseCase.java:65-74`, `CommunicationsView.vue:318-339`).
5. Not retryable (not FAILED, not EMAIL, wrong message, unknown id): 400, error toast with the server's message (`CommunicationsView.vue:340-346`).

### What happens after Send
1. The request thread saves the message and writes one `message_delivery` row (status PENDING) per recipient together with it (`src/main/java/io/github/membertracker/infrastructure/persistence/repository/CommunicationDbRepository.java:61-68`, `:103-124`), then returns at once (`SendCommunicationToAllMembersUseCase.java:64-69`, `SendCommunicationToMembersUseCase.java:57-72`). The response carries no `deliveries` list (`@JsonIgnore` on `Communication.getDeliveries()`, `Communication.java:105-106`); read them with `GET /api/communications/{id}/deliveries`.
2. One background thread per send walks the recipients in order (`SendCommunicationToAllMembersUseCase.java:66-67`, `SendCommunicationToMembersUseCase.java:60-61`).
3. For each recipient, title and message are personalised: `{{member_name}}` becomes the member's name, or "member" when the name is blank; other text is left as written (`src/main/java/io/github/membertracker/utils/MessageTemplates.java:21-28`). The stored message keeps the placeholder.
4. The email goes out with up to 3 attempts and exponential backoff by default (`src/main/java/io/github/membertracker/infrastructure/service/EmailService.java:64-66`, `:71-120`). With mail disabled, the attempt fails at once (`EmailService.java:73-76`). Settings: [../email.md](../email.md#configuration).
5. The delivery becomes SENT with a time, or FAILED with a note ("Failed after max retry attempts" for send-to-all, "Failed to send email" for the other two flows) (`SendCommunicationToAllMembersUseCase.java:90-101`, `SendCommunicationToMembersUseCase.java:85-89`). Each result is saved as that one delivery row (`MessageDeliveryRepository.save`, `SendCommunicationToAllMembersUseCase.java:117-136`, `SendCommunicationToMembersUseCase.java:105-124`); if one row cannot be saved the error is logged and the loop goes on.
6. The thread waits 100 ms before the next recipient (`SendCommunicationToAllMembersUseCase.java:104`, `SendCommunicationToMembersUseCase.java:92`).
7. SMS and WhatsApp are stubs: a send on those channels marks every delivery FAILED ("SMS not implemented" / "WhatsApp not implemented") and saves them together (`SendCommunicationToMembersUseCase.java:62-69`, `:126-136`). No screen or endpoint chooses them; the controller passes EMAIL only (`CommunicationController.java:109`, `:127`).
8. Status path: PENDING, then SENT or FAILED. DELIVERED exists in the model but nothing sets it (`src/main/java/io/github/membertracker/domain/model/MessageDelivery.java:15-17`).

## Rules
- Request body: `title` required (max 200), `messageContent` required (max 5000), optional `type` (default ANNOUNCEMENT); the client cannot set the sent date or recipient flags (`CommunicationController.java:132-139`). Details: [communication-controller.md](communication-controller.md).
- Send to all reaches active members only; send to overdue and send to one member do not check `active` (`SendCommunicationToAllMembersUseCase.java:50`, `GetMembersWithMissedPaymentsUseCase.java:23`).
- A message can be sent only once: every send builds a new message, and marking a message sent twice is refused (`src/main/java/io/github/membertracker/domain/model/Communication.java:119-125`).
- Send and retry are STAFF+ on the server too; a VOLUNTEER calling them gets 403 (`CommunicationController.java:84`, `:91`, `:98`, `:115`, `:150`).
- A send in progress is lost if the application restarts; recipients not yet reached stay PENDING ([../email.md](../email.md#what-gets-sent)).
- The same mechanism sends the automatic payment reminders: [payment-reminders.md](payment-reminders.md).

## Known issues
- Recipients column shows "-" for send-to-overdue, send-to-one and reminder messages: the screen looks for a `memberId` that `Communication` does not have (`CommunicationsView.vue:376`, `Communication.java:22-39`).
- Send-to-overdue has no active filter: inactive members with missed months are emailed ([communication-controller.md](communication-controller.md#gotchas)).
- An empty overdue match is saved as a sent message with zero recipients ([communication-controller.md](communication-controller.md#gotchas)).
- Summary cards ignore DELIVERED (`CommunicationsView.vue:311-317`).
- The confirm count comes from the member list loaded when the screen opened, so it can differ from what the server sends (`CommunicationsView.vue:248-254`).
- The list and the dialog do not refresh by themselves; pending sends show only after a reload (`CommunicationsView.vue:299-310`).
- Load and delivery-load errors are only logged to the console (`CommunicationsView.vue:244-246`, `:306-309`).
- "Months Overdue" has no minimum on the form; 0 or less fails on the server with a 400 after the confirm (`CommunicationsView.vue:30`, `CommunicationController.java:101`).
- Only two audiences (everyone, overdue) plus one member; no unsubscribe link or consent record; no durable queue ([../email.md](../email.md#known-gaps)).
- `communicationStore.js` is unused ([communications-view.md](communications-view.md#gotchas)).

## Related
- [communication-controller.md](communication-controller.md): endpoints, errors, flows
- [communications-view.md](communications-view.md): screen state and actions
- [../email.md](../email.md): SMTP settings, personalisation, delivery tracking
- [payment-reminders.md](payment-reminders.md), [dashboard.md](dashboard.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)

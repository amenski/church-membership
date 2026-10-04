# Communications

Emails to members: staff write a message and send it to all active members, to active members who are behind on dues, or to one member; everyone with access can see the list of sent messages and each one's delivery status. The screen's name is "Messages".

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Messages screen | VOLUNTEER | `/communications` (`frontend/src/router/index.js:30-35`, guard `:102-103`) |
| View the list of sent messages | VOLUNTEER | `GET /api/communications` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:64-69`) |
| View one message's deliveries | VOLUNTEER | deliveries dialog, `GET /api/communications/{id}/deliveries` (`CommunicationController.java:131-137`) |
| Send to all active members | STAFF | `POST /api/communications/send-to-all` (`CommunicationController.java:80-85`) |
| Send to active members behind by N months | STAFF | `POST /api/communications/send-to-overdue/{months}` (`CommunicationController.java:87-102`) |
| Send to one member | STAFF | `POST /api/communications/send-to-member/{memberId}` (`CommunicationController.java:104-120`) |
| Retry a failed delivery | STAFF | "Retry" button, `POST /api/communications/{id}/deliveries/{deliveryId}/retry` (`CommunicationController.java:139-145`) |

Role view of the screen:
- VOLUNTEER sees the list and the deliveries dialog, without the "New message" card and without "Retry" buttons (`frontend/src/views/CommunicationsView.vue:15`, `:349-351`).
- STAFF and ADMIN see the compose card and "Retry" (`isStaff`, `frontend/src/stores/authStore.js:39`).
- MEMBER cannot open the screen: the route guard shows an "Access denied" warning toast and sends them to their profile (`frontend/src/router/index.js:103-112`, `frontend/src/stores/authStore.js:41`); the API answers 403.

## How it works
### View the list of sent messages
1. The screen loads the active members (STAFF and above only) and all communications once on open, and the list again after every send and every retry (`CommunicationsView.vue:234-259`).
2. "Sent messages" is a ruled list, newest first by sent date. Each row shows the bold title; the sent date and time; a plain type word ("Reminder", "Announcement", "Personal"); "N recipients" (`recipientCount`) or "No deliveries recorded" when it is 0; the delivery summary as words with a status dot ("8 sent" in fern, "1 failed" in clay, "1 pending" in ochre; sent and delivered are added together and zero counts are left out); and a "View deliveries" text button.
3. The list items carry `recipientCount` and a `deliverySummary` `{ sent, failed, pending, delivered }` computed by the server; the deliveries themselves are not in the list.
4. Empty: "No messages yet." plus "Use the form above to send the first one." for STAFF and above. On a load failure a banner says the messages did not load, with "Try again".

### Compose a message (STAFF+)
1. In the "New message" card pick "Send to": "All active members", "Members behind on dues" or "One member". Behind adds "At least this many months behind" (number, minimum 1, default 1); one member adds a "Member" select of active members (`CommunicationsView.vue:20-39`).
2. Type "Subject" (up to 200 characters) and "Message" (up to 5000, with a counter); the helper line reads "Write {{member_name}} to insert each member's name." (`CommunicationsView.vue:41-56`).
3. "Send message" checks the form on the screen (subject, message, member, months) and then counts the recipients on the client (`audienceCount`, `frontend/src/utils/audienceCount.js:7-16`): every ACTIVE member for all, ACTIVE members with `consecutiveMonthsMissed >= months` for behind, 1 for one member. At 0 it shows "There is nobody to send this to." in the card and asks nothing (`CommunicationsView.vue:282-290`).
4. Otherwise a confirm dialog (`frontend/src/components/ConfirmDialog.vue`) asks "Send to N members?" ("Send to 1 member?") and says the message goes out by email and cannot be taken back. Cancel is focused when it opens. The button reads "Send to N members".
5. Confirm sends only `title` and `messageContent`, trimmed, as type ANNOUNCEMENT unless the server is told otherwise (`frontend/src/utils/communicationPayload.js:2-7`).
6. While the request runs, the confirm button is busy and both buttons are disabled (`CommunicationsView.vue:291-312`).
7. Success: the dialog closes, the form resets, a toast "Sending started" says "N members will get it in the next few minutes. Check the delivery status below." and the list reloads. The toast means the server accepted the send, not that emails arrived.
8. Failure (blank title, unknown member, months below 1, nobody to send to, 403): the dialog closes, the card shows the server's message in a banner and an error toast "Could not send message" repeats it (no toast for 403, the shared handler already shows one); the form keeps what was typed (`CommunicationsView.vue:314-327`, `frontend/src/services/api.js:82-90`).
9. Server limits: title up to 200 characters, message up to 5000 ([communication-controller.md](communication-controller.md)).

### Send to all active members (STAFF+)
1. Choose "All active members". The confirm dialog counts the active members in the loaded list (`audienceCount`).
2. `POST /api/communications/send-to-all` (`CommunicationsView.vue:297`).
3. The server looks up the active members; with none it answers 400 `COMMUNICATION_006` "There is nobody to send this to." and stores nothing (`src/main/java/io/github/membertracker/usecase/SendCommunicationToAllMembersUseCase.java:52-56`, `src/main/java/io/github/membertracker/domain/exception/CommunicationDomainException.java:14`, `:21`).
4. Otherwise it marks the message as sent and as sent to all members, prepares one PENDING delivery per member, saves the message together with its deliveries and starts the background send (`SendCommunicationToAllMembersUseCase.java:58-79`). See "What happens after Send".

### Send to members behind by N months (STAFF+)
1. Choose "Members behind on dues" and enter N. The confirm dialog counts ACTIVE members with `consecutiveMonthsMissed >= N` in the loaded list; an inactive member is never counted, as on the server.
2. `POST /api/communications/send-to-overdue/{N}`; N must be at least 1, otherwise 400 (`CommunicationController.java:90-92`).
3. The server selects active members at least N months behind, the one furthest behind first (`src/main/java/io/github/membertracker/usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), and sends by email (`CommunicationController.java:94-101`).
4. If nobody matches, the server answers 400 `COMMUNICATION_006` "There is nobody to send this to." and stores nothing (`src/main/java/io/github/membertracker/usecase/SendCommunicationToMembersUseCase.java:51-53`).

### Send to one member (STAFF+)
1. Choose "One member" and pick the member (active members only). The confirm dialog reads "Send to 1 member?".
2. `POST /api/communications/send-to-member/{memberId}` (`CommunicationsView.vue:299`).
3. An unknown member id returns 400 (`CommunicationController.java:111-112`); otherwise one PENDING delivery is created and the send runs in the background (`CommunicationController.java:113-119`).
4. The Overview's "Send reminder" button uses this same endpoint and stores the message as a REMINDER: [dashboard.md](dashboard.md).

### Inspect deliveries
1. Click "View deliveries" on a row (`CommunicationsView.vue:83-85`).
2. The dialog "Deliveries: <title>" shows the sent date and time, the totals as words (sent, failed, pending) and one ruled row per delivery: recipient name, a status word with a dot (Sent and Delivered fern, Failed clay, Pending ochre), the time, the email address and the response notes (`CommunicationsView.vue:103-145`).
3. The dialog is a snapshot taken when it opens: PENDING rows do not change until it is closed and reopened.
4. If the deliveries cannot be loaded the dialog stays open with a banner and "Try again". With none: "No deliveries were recorded for this message."

### Retry a failed delivery (STAFF+)
1. In the dialog, a "Retry" button appears on FAILED deliveries on the EMAIL channel, for STAFF and above only (`CommunicationsView.vue:349-351`).
2. The server accepts only FAILED deliveries on the EMAIL channel that belong to that message (`src/main/java/io/github/membertracker/usecase/RetryDeliveryUseCase.java:43-52`).
3. It re-sends once and the request waits for the result (`RetryDeliveryUseCase.java:58-63`). The retry personalises the text for that member.
4. Success: status becomes SENT with a time, toast "Delivery retried". Failure: status stays FAILED, notes read "Retry failed at ...", warning toast "Retry failed" (`RetryDeliveryUseCase.java:65-74`, `CommunicationsView.vue:352-369`).
5. Not retryable (not FAILED, not EMAIL, wrong message, unknown id): 400, error toast "Could not retry delivery" with the server's message (not for 403).

### What happens after Send
1. The request thread saves the message and writes one `message_delivery` row (status PENDING) per recipient together with it (`src/main/java/io/github/membertracker/infrastructure/persistence/repository/CommunicationDbRepository.java:99-104`, `:123-134`), then returns at once (`SendCommunicationToAllMembersUseCase.java:73-81`, `SendCommunicationToMembersUseCase.java:66-84`). The response carries no `deliveries` list (`@JsonIgnore` on `Communication.getDeliveries()`, `src/main/java/io/github/membertracker/domain/model/Communication.java:108-112`); read them with `GET /api/communications/{id}/deliveries`.
2. One background thread per send walks the recipients in order (`SendCommunicationToAllMembersUseCase.java:89-91`, `SendCommunicationToMembersUseCase.java:92-94`).
3. For each recipient, title and message are personalised: `{{member_name}}` becomes the member's name, or "member" when the name is blank; other text is left as written (`src/main/java/io/github/membertracker/utils/MessageTemplates.java:21-28`). The stored message keeps the placeholder.
4. The email goes out with up to 3 attempts and exponential backoff by default (`src/main/java/io/github/membertracker/infrastructure/service/EmailService.java:65-79`, `:102-104`). With mail disabled, the attempt fails at once (`EmailService.java:67-70`). Settings: [../email.md](../email.md#configuration).
5. The delivery becomes SENT with a time, or FAILED with a note ("Failed after max retry attempts" for send-to-all, "Failed to send email" for the other flows) (`SendCommunicationToAllMembersUseCase.java:108-118`, `SendCommunicationToMembersUseCase.java:103-106`). Each result is saved as that one delivery row (`MessageDeliveryRepository.save`, `SendCommunicationToAllMembersUseCase.java:134-153`, `SendCommunicationToMembersUseCase.java:122-141`); if one row cannot be saved the error is logged and the loop goes on.
6. The thread waits 100 ms before the next recipient (`SendCommunicationToAllMembersUseCase.java:121`, `SendCommunicationToMembersUseCase.java:109`).
7. SMS and WhatsApp are stubs: a send on those channels marks every delivery FAILED ("SMS not implemented" / "WhatsApp not implemented") and saves them together (`SendCommunicationToMembersUseCase.java:74-82`, `:143-153`). No screen or endpoint chooses them; the controller passes EMAIL only (`CommunicationController.java:99`, `:117`).
8. Status path: PENDING, then SENT or FAILED. DELIVERED exists in the model and in the summary, but nothing in the app sets it (`src/main/java/io/github/membertracker/domain/model/MessageDelivery.java:15-17`); the screen counts it with sent.

## Rules
- Request body: `title` required (max 200), `messageContent` required (max 5000), optional `type` (ANNOUNCEMENT, REMINDER, PERSONAL; default ANNOUNCEMENT); the client cannot set the sent date or recipient flags (`CommunicationController.java:122-129`, `src/main/java/io/github/membertracker/infrastructure/dto/SendCommunicationRequest.java`). Details: [communication-controller.md](communication-controller.md).
- Send to all and send to behind reach active members only; send to one member sends to the member chosen. Sending to nobody is a 400 and stores nothing.
- There is no draft endpoint any more (`POST /api/communications` was removed), so the frontend has no `createCommunication`.
- A message can be sent only once: every send builds a new message, and marking a message sent twice is refused (`src/main/java/io/github/membertracker/domain/model/Communication.java:141-147`).
- Send and retry are STAFF+ on the server too; a VOLUNTEER calling them gets 403 (`CommunicationController.java:81`, `:88`, `:105`, `:140`).
- A send in progress is lost if the application restarts; recipients not yet reached stay PENDING ([../email.md](../email.md#what-gets-sent)).
- The same mechanism sends the automatic payment reminders: [payment-reminders.md](payment-reminders.md).
- "Behind" means active members only: an inactive member's counter is stale and the server leaves them out.

## Known issues
- The confirm count comes from the member list loaded when the screen opened, so it can differ from what the server sends (`CommunicationsView.vue:219-221`).
- The list and the dialog do not refresh by themselves; pending sends show only after a reload or reopening the dialog.
- The screen sends every message as an ANNOUNCEMENT (the shared request builder has no type); only the Overview's reminder is stored as a REMINDER.
- Only two audiences (everyone, behind) plus one member; no unsubscribe link or consent record; no durable queue ([../email.md](../email.md#known-gaps)).
- The unused `communicationStore.js` was removed in `chore(ui): remove dead frontend code` ([communications-view.md](communications-view.md#gotchas)).

## Related
- [communication-controller.md](communication-controller.md): endpoints, errors, flows
- [communications-view.md](communications-view.md): screen state and actions
- [../email.md](../email.md): SMTP settings, personalisation, delivery tracking
- [payment-reminders.md](payment-reminders.md), [dashboard.md](dashboard.md)
- [../authentication.md](../authentication.md), [../architecture.md](../architecture.md)

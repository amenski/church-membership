# Communications

Emails to members: staff write a message and send it to all members with status MEMBER, to those of them who are behind on dues, or to one member; everyone with access can see the list of sent messages and each one's delivery status. The screen's name is "Messages".

## Who can do what
Roles from `@PreAuthorize` and route meta; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| Open the Messages screen | VOLUNTEER | `/communications` (`frontend/src/router/index.js:30-35`, guard `:108-109`) |
| View the list of sent messages | VOLUNTEER | `GET /api/communications` (`src/main/java/io/github/membertracker/infrastructure/CommunicationController.java:64-69`) |
| View one message's deliveries | VOLUNTEER | deliveries dialog, `GET /api/communications/{id}/deliveries` (`CommunicationController.java:131-137`) |
| Send to all active members | STAFF | `POST /api/communications/send-to-all` (`CommunicationController.java:80-85`) |
| Send to active members behind by N months | STAFF | `POST /api/communications/send-to-overdue/{months}` (`CommunicationController.java:87-102`) |
| Send to one member | STAFF | `POST /api/communications/send-to-member/{memberId}` (`CommunicationController.java:104-120`) |
| Retry a failed delivery | STAFF | "Retry" button, `POST /api/communications/{id}/deliveries/{deliveryId}/retry` (`CommunicationController.java:139-145`) |

Role view of the screen:
- VOLUNTEER sees the list and the deliveries dialog, without the "New message" card and without "Retry" buttons (`frontend/src/views/CommunicationsView.vue:15`, `:353-355`).
- STAFF and ADMIN see the compose card and "Retry" (`isStaff`, `frontend/src/stores/authStore.js:39`).
- MEMBER cannot open the screen: the route guard shows an "Access denied" warning toast and sends them to their profile (`frontend/src/router/index.js:109-118`, `frontend/src/stores/authStore.js:41`); the API answers 403.

## How it works
### View the list of sent messages
1. The screen loads the active members (STAFF and above only) and all communications once on open, and the list again after every send and every retry (`CommunicationsView.vue:238-263`).
2. "Sent messages" is a ruled list, newest first by sent date. Each row shows the bold title; the sent date and time; a plain type word ("Reminder", "Announcement", "Personal"); "N recipients" (`recipientCount`) or "No deliveries recorded" when it is 0; the delivery summary as badges ("8 delivered" in fern, "1 failed" in clay, "1 pending" in ochre; sent and delivered are added together and zero counts are left out); and a "View deliveries" text button.
3. The list items carry `recipientCount` and a `deliverySummary` `{ sent, failed, pending, delivered }` computed by the server; the deliveries themselves are not in the list.
4. Empty: "No messages yet." plus "Use the form above to send the first one." for STAFF and above. On a load failure a banner says the messages did not load, with "Try again".

### Compose a message (STAFF+)
1. In the "New message" card pick "Send to": "Everyone", "Behind on dues" or "One member". Behind adds "At least this many months behind" (number, minimum 1, default 1); one member adds a "Member" select of active members (a member without an email is marked "(no email)" and cannot be chosen to send to; `CommunicationsView.vue:20-39`).
2. Type "Subject" (up to 200 characters) and "Message" (up to 5000, with a counter); the helper line reads "Write {{member_name}} to insert each member's name." (`CommunicationsView.vue:41-56`).
3. Beside the form (below it on a narrow screen) the "Who gets this" card lists who the message reaches: a count line ("3 members owe 2 or more months. 2 of them have an email."), then each member with the months behind, the year strip and a badge "Will get the email" or "Skipped, no email" with the phone number to call; members who share an address show "Skipped, shares an address". It is computed on the client from the members list and the paid months of the last 12 (`GET /api/payments/paid-months`; `previewRecipients`, `frontend/src/utils/audiencePreview.js`).
4. The send button reads "Send to N people", N being the recipients who will get an email (members without an email are skipped, members sharing an address count once, case ignored; only status MEMBER). Pressing it checks the form (subject, message, member, months). At 0 it shows "There is nobody to send this to." in the card and asks nothing.
5. Otherwise a confirm dialog (`frontend/src/components/ConfirmDialog.vue`) asks "Send to N people?" ("Send to 1 person?"), says the message goes out by email and cannot be taken back, and names who is skipped. Cancel is focused when it opens. The button reads "Send to N people".
6. Confirm sends only `title` and `messageContent`, trimmed, as type ANNOUNCEMENT unless the server is told otherwise (`frontend/src/utils/communicationPayload.js:2-7`).
7. While the request runs, the confirm button is busy and both buttons are disabled (`CommunicationsView.vue:295-316`).
8. Success: the dialog closes, the form resets, a toast "Sending started" says "N people will get it in the next few minutes. Check the delivery status below." and the list reloads. The toast means the server accepted the send, not that emails arrived.
9. Failure (blank title, unknown member, months below 1, nobody to send to, 403): the dialog closes, the card shows the server's message in a banner and an error toast "Could not send message" repeats it (no toast for 403, the shared handler already shows one); the form keeps what was typed (`CommunicationsView.vue:318-331`, `frontend/src/services/api.js:82-90`).
10. Server limits: title up to 200 characters, message up to 5000 ([communication-controller.md](communication-controller.md)).

### Send to all active members (STAFF+)
1. Choose "Everyone". The send button and the confirm dialog count the people with an email in the loaded list (`previewRecipients`).
2. `POST /api/communications/send-to-all` (`CommunicationsView.vue:301`).
3. The server looks up the MEMBER-status members, drops those without an email and keeps one member per address (`Recipients.reachable`: the lowest id wins, case and spaces ignored); with none left it answers 400 `COMMUNICATION_006` "There is nobody to send this to." and stores nothing (`src/main/java/io/github/membertracker/usecase/SendCommunicationToAllMembersUseCase.java:53-57`, `src/main/java/io/github/membertracker/domain/exception/CommunicationDomainException.java:14`, `:21`).
4. Otherwise it marks the message as sent and as sent to all members, prepares one PENDING delivery per member, saves the message together with its deliveries and starts the background send (`SendCommunicationToAllMembersUseCase.java:59-80`). See "What happens after Send".

### Send to members behind by N months (STAFF+)
1. Choose "Behind on dues" and enter N. The preview, the send button and the confirm dialog use MEMBER-status members with `consecutiveMonthsMissed >= N` in the loaded list, furthest behind first; any other status is never counted, as on the server.
2. `POST /api/communications/send-to-overdue/{N}`; N must be at least 1, otherwise 400 (`CommunicationController.java:90-92`).
3. The server selects MEMBER-status members at least N months behind, the one furthest behind first (`src/main/java/io/github/membertracker/usecase/GetMembersWithMissedPaymentsUseCase.java:22-24`), and sends by email (`CommunicationController.java:94-101`).
4. Members without an email are dropped and shared addresses count once, as for send to all. If nobody is left, the server answers 400 `COMMUNICATION_006` "There is nobody to send this to." and stores nothing (`src/main/java/io/github/membertracker/usecase/SendCommunicationToMembersUseCase.java:52-54`).

### Send to one member (STAFF+)
1. Choose "One member" and pick the member (active members only). The preview shows that member; the confirm dialog reads "Send to 1 person?".
2. `POST /api/communications/send-to-member/{memberId}` (`CommunicationsView.vue:303`).
3. A member with no email returns 400 `COMMUNICATION_007` "Member '<name>' has no email address, so there is nothing to send to." and stores nothing. An unknown member id returns 400 (`CommunicationController.java:111-112`); otherwise one PENDING delivery is created and the send runs in the background (`CommunicationController.java:113-119`).
4. The Overview's "Send reminder" button uses this same endpoint and stores the message as a REMINDER: [dashboard.md](dashboard.md).

### Inspect deliveries
1. Click "View deliveries" on a row (`CommunicationsView.vue:83-85`).
2. The dialog "Deliveries: <title>" shows the sent date and time, the totals as badges (delivered, failed, pending) and one ruled row per delivery, failed ones first: recipient name, a status word with a dot (Sent and Delivered both read "Delivered" in fern, Failed clay, Pending ochre), the time, the email address and the response notes, followed by "N attempts" ("1 attempt") when the server counted any (`CommunicationsView.vue:103-148`).
3. The dialog is a snapshot taken when it opens: PENDING rows do not change until it is closed and reopened.
4. If the deliveries cannot be loaded the dialog stays open with a banner and "Try again". With none: "No deliveries were recorded for this message."

### Retry a failed delivery (STAFF+)
1. In the dialog, a "Retry" button appears on FAILED deliveries on the EMAIL channel, for STAFF and above only (`CommunicationsView.vue:353-355`).
2. The server accepts only FAILED deliveries on the EMAIL channel that belong to that message (`src/main/java/io/github/membertracker/usecase/RetryDeliveryUseCase.java:44-53`).
3. It re-sends once (one full cycle of tries) and the request waits for the result (`RetryDeliveryUseCase.java:59-66`); the tries it made are added to the delivery's attempts. The retry personalises the text for that member.
4. Success: status becomes SENT with a time, toast "Delivery retried". Failure: status stays FAILED, notes read "Retry failed at ...", warning toast "Retry failed" (`RetryDeliveryUseCase.java:68-77`, `CommunicationsView.vue:356-373`).
5. Not retryable (not FAILED, not EMAIL, the member no longer has an email, wrong message, unknown id): 400, error toast "Could not retry delivery" with the server's message (not for 403).

### What happens after Send
1. The request thread saves the message and writes one `message_delivery` row (status PENDING) per recipient together with it (`src/main/java/io/github/membertracker/infrastructure/persistence/repository/CommunicationDbRepository.java:99-104`, `:123-134`), then returns at once (`SendCommunicationToAllMembersUseCase.java:74-82`, `SendCommunicationToMembersUseCase.java:67-85`). The response carries no `deliveries` list (`@JsonIgnore` on `Communication.getDeliveries()`, `src/main/java/io/github/membertracker/domain/model/Communication.java:108-112`); read them with `GET /api/communications/{id}/deliveries`.
2. One background thread per send walks the recipients in order (`SendCommunicationToAllMembersUseCase.java:90-92`, `SendCommunicationToMembersUseCase.java:93-95`).
3. For each recipient, title and message are personalised: `{{member_name}}` becomes the member's name, or "member" when the name is blank; other text is left as written (`src/main/java/io/github/membertracker/utils/MessageTemplates.java:21-28`). The stored message keeps the placeholder.
4. The email goes out with up to 3 attempts and exponential backoff by default (`src/main/java/io/github/membertracker/infrastructure/service/EmailService.java:65-79`, `:102-104`). With mail disabled, the attempt fails at once (`EmailService.java:67-70`). Settings: [../email.md](../email.md#configuration).
5. The number of tries made is stored too (`attempts`: 1 when the first try worked, 3 when all three failed, 0 when no try was made because mail is off). The delivery becomes SENT with a time, or FAILED with a note ("Failed after max retry attempts" for send-to-all, "Failed to send email" for the other flows) (`SendCommunicationToAllMembersUseCase.java:111-121`, `SendCommunicationToMembersUseCase.java:106-109`). Each result is saved as that one delivery row (`MessageDeliveryRepository.save`, `SendCommunicationToAllMembersUseCase.java:137-157`, `SendCommunicationToMembersUseCase.java:125-145`); if one row cannot be saved the error is logged and the loop goes on.
6. The thread waits 100 ms before the next recipient (`SendCommunicationToAllMembersUseCase.java:124`, `SendCommunicationToMembersUseCase.java:112`).
7. SMS and WhatsApp are stubs: a send on those channels marks every delivery FAILED ("SMS not implemented" / "WhatsApp not implemented") and saves them together (`SendCommunicationToMembersUseCase.java:75-83`, `:147-157`). No screen or endpoint chooses them; the controller passes EMAIL only (`CommunicationController.java:99`, `:117`).
8. Status path: PENDING, then SENT or FAILED. DELIVERED exists in the model and in the summary, but nothing in the app sets it (`src/main/java/io/github/membertracker/domain/model/MessageDelivery.java:17-19`); the screen counts it with sent.

## Rules
- Request body: `title` required (max 200), `messageContent` required (max 5000), optional `type` (ANNOUNCEMENT, REMINDER, PERSONAL; default ANNOUNCEMENT); the client cannot set the sent date or recipient flags (`CommunicationController.java:122-129`, `src/main/java/io/github/membertracker/infrastructure/dto/SendCommunicationRequest.java`). Details: [communication-controller.md](communication-controller.md).
- Only a member with status MEMBER can be messaged: `Recipients.reachable` drops every other status (inactive, deceased, transferred, archived) as well as members without an email, so send to all and send to behind reach MEMBER-status members with an email only, one message per address. Send to one member sends to the member chosen if they have an email; a member who is not MEMBER is refused with a 400 `COMMUNICATION_008` and nothing is stored. A recipient count (`recipientCount`, "Send to N members") is a count of addresses. Sending to nobody is a 400 and stores nothing. A recipient's name, email and phone come from the member's person row (the `member` columns were dropped at step 12), and the delivery JSON's `recipient` has no `active` field, only `status`.
- There is no draft endpoint any more (`POST /api/communications` was removed), so the frontend has no `createCommunication`.
- A message can be sent only once: every send builds a new message, and marking a message sent twice is refused (`src/main/java/io/github/membertracker/domain/model/Communication.java:141-147`).
- Send and retry are STAFF+ on the server too; a VOLUNTEER calling them gets 403 (`CommunicationController.java:81`, `:88`, `:105`, `:140`).
- A send in progress is lost if the application restarts; recipients not yet reached stay PENDING ([../email.md](../email.md#what-gets-sent)).
- The same mechanism sends the automatic payment reminders: [payment-reminders.md](payment-reminders.md).
- "Behind" means MEMBER-status members only: another status's counter is stale and the server leaves them out.

## Known issues
- The confirm count comes from the member list loaded when the screen opened, so it can differ from what the server sends (`CommunicationsView.vue:223-225`).
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

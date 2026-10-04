# CommunicationsView

`frontend/src/views/CommunicationsView.vue` (no Pinia store for data)

The Messages page: compose an email to members, see what was sent and inspect or retry deliveries. Route `/communications` (nav label and page title "Messages"), minimum role VOLUNTEER (`frontend/src/router/index.js:30-35`, guard `:108-109`); sending and retrying need STAFF and above on the server ([communication-controller.md](communication-controller.md), [../authentication.md](../authentication.md)). Built on Tailwind and the shared components.

Paths below are relative to `frontend/src/`.

## What the user sees
- Page head "Messages", lead "Email members and see what was delivered."
- **New message card (STAFF and above only; a VOLUNTEER sees no form):**
  - "Send to": "All active members", "Members behind on dues" or "One member".
  - For behind: "At least this many months behind" (number, min 1, default 1). For one member: "Member" (select of ACTIVE members only, sorted by name).
  - "Subject" (up to 200 characters), "Message" (up to 5000, counter) with the helper line "Write {{member_name}} to insert each member's name."
  - "Send message" (busy label "Sending...").
- **Confirm dialog** (`components/ConfirmDialog.vue`): "Send to N members?" ("Send to 1 member?"), the text `"<subject>" goes out by email. You cannot take it back.`, buttons "Cancel" (focused when it opens) and "Send to N members". N is computed on the client by `audienceCount` (`utils/audienceCount.js`): every ACTIVE member for ALL, ACTIVE members with `consecutiveMonthsMissed >= months` for behind (an inactive member never counts, as on the server), 1 for one member. When N is 0 nothing is asked: the card shows "There is nobody to send this to."
- **Sent messages** (a ruled list, newest first by sent date): bold title; sent date and time (local), a plain type word ("Reminder", "Announcement", "Personal"), "N recipients" (`recipientCount`; 0 shows "No deliveries recorded"); the delivery summary as words with a status dot: "8 sent" (sent plus delivered, fern), "1 failed" (clay), "1 pending" (ochre), zero counts left out (`deliverySummaryParts`); a "View deliveries" text button per row.
- **Deliveries dialog** (`BaseModal` lg), titled "Deliveries: <message title>": sent date and time, the totals as words, then one ruled row per delivery: recipient name, a `StatusLabel` (Sent and Delivered fern, Failed clay, Pending ochre), time, the email address and the response notes beneath, with "3 attempts" ("1 attempt") beside the note in small muted text when `attempts` is above 0 (`attemptsLabel`, `frontend/src/utils/messageHistory.js:8-11`); STAFF and above get a "Retry" button on FAILED EMAIL deliveries only (busy label "Retrying..." per row). Empty: "No deliveries were recorded for this message."
- Empty state: "No messages yet." plus "Use the form above to send the first one." for STAFF and above. A load failure shows a banner "The messages did not load. Check your connection and try again." with "Try again".

## State
Component `data()` (`views/CommunicationsView.vue:197-215`), not the Pinia store:

| Field | Meaning |
|-------|---------|
| `members` | loaded for STAFF and above only: the "One member" select and the recipient count |
| `communications` | rows of the history (list items carry `recipientCount` and `deliverySummary`, not the deliveries) |
| `loaded`, `loadError` | first load finished, and failed |
| `form` | `recipientType` (`ALL`/`OVERDUE`/`SPECIFIC`), `memberId`, `monthsOverdue`, `subject`, `message` |
| `formErrors`, `sendError` | field messages, and the banner text of the card |
| `sending`, `confirmOpen` | a send is in flight; the confirm dialog is open |
| `selectedMessage`, `deliveriesOpen`, `deliveries`, `deliveriesLoading`, `deliveriesError` | the deliveries dialog |
| `retryingIds` | delivery ids with a retry in flight |

## Actions
| Method | Does |
|--------|------|
| `loadData` (`:238`) | `api.getMembers` (STAFF and above) and `api.getCommunications` in parallel |
| `refreshMessages` (`:256`) | reloads only the history, after a send or a retry |
| `validateForm`, `askToSend` (`:273`, `:286`) | checks the fields, stops at 0 recipients, otherwise opens the confirm dialog |
| `sendMessage` (`:295`) | body from `buildCommunicationRequest` (`{ title, messageContent }`, the type is left to the server, so ANNOUNCEMENT); ALL -> `api.sendToAllMembers(payload)`; behind -> `api.sendToOverdueMembers(months, payload)`; one -> `api.sendToMember(memberId, payload)`. Success: closes the dialog, clears the form, toast "Sending started" ("N members will get it in the next few minutes. Check the delivery status below."), reloads the history |
| `showSendError` (`:318`) | `fieldErrors` (`title`, `messageContent`) go under Subject and Message; any other 400 detail goes in the card's `AlertBanner`, plus an error toast "Could not send message" (no toast for 403: the shared handler already shows one) |
| `openDeliveries`, `loadDeliveries` (`:332`, `:338`) | `api.getCommunicationDeliveries(id)` into the dialog; a failure shows "Try again" |
| `canRetry`, `retryDelivery` (`:353`, `:356`) | `api.retryDelivery(commId, deliveryId)`; swaps the row with the response. Toast "Delivery retried" when it is no longer FAILED, a warning "Retry failed" when it stays FAILED, an error toast "Could not retry delivery" on an exception |

## Backend facts
- `GET /api/communications` items carry `recipientCount` (int) and `deliverySummary` `{ sent, failed, pending, delivered }`; `deliveries` is not in the list, `GET /api/communications/{id}/deliveries` has them. The draft endpoint `POST /api/communications` no longer exists, so the view and `api.js` have no `createCommunication`.
- Sending to nobody answers 400 "There is nobody to send this to." and stores nothing. "Overdue" means ACTIVE members only on the server.
- Send endpoints: `sendToAllMembers`, `sendToOverdueMembers(months, payload)`, `sendToMember(memberId, payload)`, retry `retryDelivery(communicationId, deliveryId)`.

## Collaborators
- API methods: `services/api.js:379-401` (`getCommunications`, `getCommunicationDeliveries`, `retryDelivery`, `sendToAllMembers`, `sendToOverdueMembers`, `sendToMember`); `request` returns `response.data` (`services/api.js:285-288`)
- `utils/communicationPayload.js` `buildCommunicationRequest` (frontend half of the shared contract fixture, `__tests__/utils/communicationPayload.test.js`)
- `utils/audienceCount.js` and `utils/messageHistory.js` (`attemptsLabel`, `typeLabel`, `deliverySummaryParts`, `deliveryStatus`, `countDeliveries`, `sortMessages`), each with a test in `__tests__/utils/`
- Components: `PageHead`, `SectionTitle`, `AlertBanner`, `BaseButton`, `BaseInput`, `BaseSelect`, `BaseTextarea`, `BaseModal`, `ConfirmDialog`, `StatusLabel`, `TextButton`, `EmptyNote`
- `stores/authStore.js:39` `isStaff` (`hasRole('STAFF')`); `useAppStore().addNotification` for toasts
- Backend: [communication-controller.md](communication-controller.md)

## Errors
- The api interceptor copies the server `detail` into `error.message` and the field list into `error.fieldErrors` (`services/api.js:82-90`), so a 400 (blank subject, nobody to send to) is shown as it came.
- A load failure shows the banner; a deliveries failure shows its own "Try again" inside the dialog. 401 and 403 also raise a global notification from the interceptor (`services/api.js:174`, `:198`).

## Side effects
- Sends real emails (send-to-all, send-to-overdue, send-to-member), all on a background thread; the response carries no deliveries (read them with the deliveries endpoint).
- Retry re-sends one email synchronously; the request blocks for the SMTP attempts (a few seconds).
- No localStorage or cache writes.

## Gotchas
- The dialog is a snapshot: sends deliver in the background, so `PENDING` rows do not update until the dialog is reopened.
- The confirm count is computed from the loaded `members` list, so it matches the server only while that list is fresh (the server recomputes overdue members on send).
- Without a mail server every send ends in FAILED deliveries (the demo stack has none), which is how Retry is reached.
- The unused, non-working `communicationStore.js` was removed in `chore(ui): remove dead frontend code`.

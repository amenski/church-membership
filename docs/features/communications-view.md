# CommunicationsView

`frontend/src/views/CommunicationsView.vue` (store: `frontend/src/stores/communicationStore.js`, see Gotchas)

Page to compose and send emails to members, list past communications and inspect or retry deliveries. Route `/communications`, minimum role VOLUNTEER (`frontend/src/router/index.js:30-33`, guard `:101-102`); sending and retrying need STAFF+ on the server ([communication-controller.md](communication-controller.md), [../authentication.md](../authentication.md)).

Paths below are relative to `frontend/src/`.

## State
Component `data()` (`views/CommunicationsView.vue:205-225`), not the Pinia store:

| Field | Meaning |
|-------|---------|
| `members` | loaded for the "Specific Member" dropdown and recipient label |
| `communications` | rows of the "Recent Communications" table |
| `selectedCommunication`, `deliveries` | communication open in the delivery dialog and its deliveries |
| `deliverySummary` | `{sent, failed, pending}` counts for the dialog cards |
| `retryingIds` | delivery ids with a retry in flight (disables button, shows spinner) |
| `newMessage` | form model: `recipientType` (`ALL`/`OVERDUE`/`SPECIFIC`), `memberId`, `monthsOverdue`, `subject`, `message` |

No `isLoading` / `error` state in the view.

## UI
- Send form (`:9-44`): recipient select, member select (SPECIFIC), months input (OVERDUE), subject, message. Shown to every role that can open the page.
- List (`:64-88`): date, title, recipients, delivery button, "View". Delivery button and "View" render only when `comm.sentToAllMembers` (`:70`, `:81`).
- Delivery dialog (`:96-187`, Bootstrap modal):
  - Header: title and sent date (`:104-107`)
  - Summary cards Sent / Failed / Pending (`:110-135`)
  - Table: recipient, email, status badge, delivered at, notes, actions (`:139-179`)
  - Per-row Retry button: only when `status === 'FAILED'` and `authStore.isStaff` (`:162-175`); disabled with spinner while in `retryingIds`

## Actions
| Method | Does |
|--------|------|
| `loadData` (`:230`) | `api.getMembers` + `api.getCommunications` in parallel; fills `members`, `communications` |
| `sendMessage` (`:242`) | `ALL` -> `api.sendToAllMembers`; `OVERDUE` -> `api.sendToOverdueMembers(months, body)`; `SPECIFIC` -> `api.createCommunication`. Then reloads and resets the form |
| `showDeliveryDetails` (`:263`) | `api.getCommunicationDeliveries(id)`, fills `deliveries`, recomputes summary, opens modal |
| `calculateSummary` (`:275`) | counts `SENT`, `FAILED`, `PENDING` (not `DELIVERED`) |
| `retryDelivery` (`:282`) | `api.retryDelivery(commId, deliveryId)`; swaps the row with the response, recomputes summary; toast "Delivery re-sent" if `SENT`, warning "Retry failed" otherwise, error toast with the server `detail` on exception (`:304-310`) |
| `getDeliverySummary` (`:315`) | button label from embedded `comm.deliveries`, else "Click to view" |

## Collaborators
- API methods: `services/api.js:398-425` (`getCommunications`, `getCommunicationDeliveries`, `retryDelivery`, `createCommunication`, `sendToAllMembers`, `sendToOverdueMembers`); `request` returns `response.data` (`services/api.js:285-288`)
- `stores/authStore.js:41` `isStaff` (`hasRole('STAFF')`)
- `useAppStore().addNotification` for retry toasts (`views/CommunicationsView.vue:290-310`)
- Bootstrap `Modal` (`:269`)
- Backend: [communication-controller.md](communication-controller.md)

## Errors
- Retry: server `detail` shown as error toast (`services/api.js:82-89` copies `detail` to `error.message`; `views/CommunicationsView.vue:308`).
- Load, send and delivery-load failures: `console.error` only in the view (`:238-240`, `:259-261`, `:270-273`); 401/403 also raise a global notification from the api interceptor (`services/api.js:177-224`). A 400 on send is therefore silent.

## Side effects
- Sends real emails (send-to-all, send-to-overdue); the SPECIFIC option sends nothing (see Gotchas).
- Retry re-sends one email synchronously; the request blocks for the SMTP attempts.
- No localStorage or cache writes.

## Gotchas
- The form posts `subject` and `message` (`:35`, `:40`, body is `this.newMessage` at `:245-249`), but the server requires `title` and `messageContent` (`src/main/java/io/github/membertracker/domain/model/Communication.java:24-30`). Nothing in `services/api.js` renames them, so sends fail with 400 "One or more fields are invalid", and listed rows would show no title.
- "Specific Member" calls `createCommunication` (`:249`): saves a draft, emails nobody, and `memberId` is not a `Communication` field.
- Delivery details are reachable only for `sentToAllMembers` communications (`:70`, `:81`); overdue sends cannot be inspected or retried from this page.
- VOLUNTEER sees the send form; submit returns 403, shown only by the global "Access Denied" notification (`services/api.js:198-224`), the view adds nothing (`views/CommunicationsView.vue:259-261`).
- The dialog is a snapshot: sends deliver in the background, so `PENDING` rows do not update until the dialog is reopened (`:263-274`).
- Summary cards ignore `DELIVERED` status (`:275-281`), though it has a badge (`:351`).
- `communicationStore.js` is not used by this view; it is only re-exported (`stores/index.js:5`). Its getters read `comm.status` and `comm.recipientCount` (`stores/communicationStore.js:32`, `:53`) which the backend `Communication` does not have, and `sendToAllMembers` / `sendToOverdueMembers` expect `result.communication` (`:166`, `:189`) while the API returns the communication directly. It has no delivery or retry actions.

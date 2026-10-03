# CommunicationsView

`frontend/src/views/CommunicationsView.vue` (store: `frontend/src/stores/communicationStore.js`, see Gotchas)

Page to compose and send emails to members, list past communications and inspect or retry deliveries. Route `/communications`, minimum role VOLUNTEER (`frontend/src/router/index.js:31-34`, guard `:102-103`); sending and retrying need STAFF+ on the server ([communication-controller.md](communication-controller.md), [../authentication.md](../authentication.md)).

Paths below are relative to `frontend/src/`.

## State
Component `data()` (`views/CommunicationsView.vue:210-231`), not the Pinia store:

| Field | Meaning |
|-------|---------|
| `sending` | a send is in flight (disables the submit button, shows a spinner) |
| `members` | loaded for the "Specific Member" dropdown, the confirm count and recipient label |
| `communications` | rows of the "Recent Communications" table |
| `selectedCommunication`, `deliveries` | communication open in the delivery dialog and its deliveries |
| `deliverySummary` | `{sent, failed, pending}` counts for the dialog cards |
| `retryingIds` | delivery ids with a retry in flight (disables button, shows spinner) |
| `newMessage` | form model: `recipientType` (`ALL`/`OVERDUE`/`SPECIFIC`), `memberId`, `monthsOverdue`, `subject`, `message` |

No `isLoading` / `error` state in the view.

## UI
- Send form (`:5-50`): recipient select, member select (SPECIFIC), months input (OVERDUE), subject, message (with a hint that `{{member_name}}` is replaced by each member's name). Rendered only when `authStore.isStaff`; VOLUNTEERs see the list but no form.
- List (`:65-96`): date, title, recipients, delivery button, "View". Delivery button and "View" render whenever `comm.sentDate` is set (`:74`, `:85`).
- Delivery dialog (`:99-190`, Bootstrap modal):
  - Header: title and sent date (`:107-110`)
  - Summary cards Sent / Failed / Pending (`:113-138`)
  - Table: recipient, email, status badge, delivered at, notes, actions (`:141-181`)
  - Per-row Retry button: only when `status === 'FAILED'` and `authStore.isStaff` (`:166-177`); disabled with spinner while in `retryingIds`

## Actions
| Method | Does |
|--------|------|
| `loadData` (`:236`) | `api.getMembers` + `api.getCommunications` in parallel; fills `members`, `communications` |
| `countRecipients` (`:248`) | recipient count from the loaded `members`: active members for ALL, `consecutiveMonthsMissed >= months` for OVERDUE |
| `sendMessage` (`:255`) | builds the body with `buildCommunicationRequest`; confirms before ALL/OVERDUE sends (`:260`); `ALL` -> `api.sendToAllMembers(payload)`; `OVERDUE` -> `api.sendToOverdueMembers(months, payload)`; `SPECIFIC` -> `api.sendToMember(memberId, payload)`. Sets `sending` around the call, toasts success or the server error, then reloads and resets the form |
| `showDeliveryDetails` (`:299`) | `api.getCommunicationDeliveries(id)`, fills `deliveries`, recomputes summary, opens modal |
| `calculateSummary` (`:311`) | counts `SENT`, `FAILED`, `PENDING` (not `DELIVERED`) |
| `retryDelivery` (`:318`) | `api.retryDelivery(commId, deliveryId)`; swaps the row with the response, recomputes summary; toast "Delivery re-sent" if `SENT`, warning "Retry failed" otherwise, error toast with the server `detail` on exception (`:326-347`) |
| `getDeliverySummary` (`:351`) | button label from embedded `comm.deliveries`, else "Click to view" |

## Collaborators
- API methods: `services/api.js:398-429` (`getCommunications`, `getCommunicationDeliveries`, `retryDelivery`, `createCommunication`, `sendToAllMembers`, `sendToOverdueMembers`, `sendToMember`); `request` returns `response.data` (`services/api.js:285-288`)
- `utils/communicationPayload.js` `buildCommunicationRequest` (frontend half of the shared contract fixture, `frontend/src/__tests__/utils/communicationPayload.test.js`)
- `stores/authStore.js:41` `isStaff` (`hasRole('STAFF')`)
- `useAppStore().addNotification` for send and retry toasts (`views/CommunicationsView.vue:274-281`, `:289-295`, `:326-347`)
- Bootstrap `Modal` (`:305`)
- Backend: [communication-controller.md](communication-controller.md)

## Errors
- Send: success toast "Sending started", failure error toast with `error.message` (`:274-295`) — the api interceptor copies the server `detail` into `error.message` (`services/api.js:82-89`), so a 400 (blank title, unknown member) is visible.
- Retry: server `detail` shown as error toast (`services/api.js:82-89`; `views/CommunicationsView.vue:341`).
- Load and delivery-load failures: `console.error` only in the view (`:244-246`, `:306-309`); 401/403 also raise a global notification from the api interceptor (`services/api.js:177-224`).

## Side effects
- Sends real emails (send-to-all, send-to-overdue, send-to-member), all on a background thread; the response carries no deliveries (read them with the deliveries endpoint).
- Retry re-sends one email synchronously; the request blocks for the SMTP attempts.
- No localStorage or cache writes.

## Gotchas
- The dialog is a snapshot: sends deliver in the background, so `PENDING` rows do not update until the dialog is reopened (`:299-310`).
- Summary cards ignore `DELIVERED` status (`:311-317`), though it has a badge (`:387`).
- The confirm count is computed from the loaded `members` list, so it matches the server only while that list is fresh (the server recomputes overdue members on send).
- `communicationStore.js` is not used by this view; it is only re-exported (`stores/index.js:5`). Its getters read `comm.status` and `comm.recipientCount` (`stores/communicationStore.js:32`, `:53`) which the backend `Communication` does not have, and `sendToAllMembers` / `sendToOverdueMembers` expect `result.communication` (`:166`, `:189`) while the API returns the communication directly. It has no delivery or retry actions.

# ActivityView

`frontend/src/views/ActivityView.vue` (no Pinia store for data)

The Activity page: who changed or exported what, newest first. Route `/activity`, minimum role ADMIN (`frontend/src/router/index.js:36-41`; the guard that turns a lower role away is `frontend/src/router/index.js:108-118`). The rail shows its link only to ADMIN (`frontend/src/App.vue:60`, icon `bi-clock-history`). Overview and rules: [activity.md](activity.md); API: [activity-log-controller.md](activity-log-controller.md).

## What the user sees
- Page head "Activity", lead "Who changed or exported what, newest first. Only administrators can see this."
- A "Show" select (`#filter-type`): "All activity" plus one option per type with a plain label (`activityTypeLabel`, e.g. "Member added", "Payments exported"), and "Clear filter" when one is set. The filter works on the entries already loaded; it does not ask the server.
- A count line (`aria-live="polite"`): "Latest 50 entries", or "3 of the latest 50 entries" while filtered.
- md and up: a ruled table with columns When (local date and time through `formatDate`, "Oct 4, 2026, 3:08 AM"), Who (the email, or "System" for the scheduled job), What happened (the server's description). Below md: the same rows stacked: description, time, actor.
- "Show more" raises the limit by 50 up to 200 and reloads (`api.getActivityLog(limit)`). It is shown only while the last answer was full (`entries.length >= limit`) and the limit is below 200. When it goes away, focus moves to the closing sentence ("That is everything recorded so far." or "This is the most the screen shows: the latest 200 entries.", `ref="endNote"`) so keyboard users keep their place.
- Empty: "No activity recorded yet." A filter that matches nothing: "No <type> entries in the latest N." with "Clear filter" (and "Show more to look further back." when more can be loaded). A load failure: a banner "The activity did not load. Check your connection and try again." with "Try again"; a failed "Show more" keeps the entries and the old limit.
- Keyboard: the select works with the arrow and letter keys; "Show more" and "Try again" are real buttons (Enter and Space).

## Who may open it
ADMIN only. For STAFF, VOLUNTEER and MEMBER the nav item is not rendered, and opening `/activity` directly shows the existing "Access denied" warning ("You don't have access to that page.") and goes to their home page (`/dashboard`, or `/profile` for a MEMBER). The API answers 403 as well. Covered by `frontend/src/__tests__/router/guard.test.js`.

## Endpoints
| Method | Path | api.js | Used by |
|--------|------|--------|---------|
| GET | `/activity-log?limit=N` (ADMIN) | `frontend/src/services/api.js:404` | `load` |

## State and helpers
Local component `data()` (`frontend/src/views/ActivityView.vue:98-106`): `entries`, `limit` (50), `type` ("ALL" or an `ActivityType`), `loaded`, `loadError`, `loadingMore`. Computed `visible` (filtered entries, `:109`) and `canShowMore` (`:113`). Methods `load` (`:121`), `showMore` (`:133`).

Pure helpers in `frontend/src/utils/activityLog.js` (tested in `frontend/src/__tests__/utils/activityLog.test.js`): `ACTIVITY_TYPES` (value and label for every type the server records, in filter order), `activityTypeLabel(type)` (unknown types show as they came), `actorLabel(actor)` ("System" for "system" or a missing actor), `filterByType(entries, type)`, `nextLimit(limit)`, `PAGE_SIZE` 50, `MAX_LIMIT` 200. `ACTIVITY_TYPES` mirrors `domain/enumeration/ActivityType.java`; add a type in both places.

## Collaborators
- `frontend/src/services/api.js` (default import); `formatDate` from `frontend/src/utils/index.js`
- Components: `PageHead`, `AlertBanner`, `BaseButton`, `EmptyNote`, `TextButton`

## Side effects
None; the screen only reads.

## Gotchas
- The time is the server's local clock as written (the API sends a date-time without a zone); the browser reads it as local time, which is right when both are in the same zone.
- The filter only sees what is loaded: pick a type, then "Show more" to look further back.
- The latest 200 entries are the most the screen can show; older ones are only in the database.

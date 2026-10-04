# ActivityView

`frontend/src/views/ActivityView.vue` (no Pinia store for data)

The Activity page: who changed or exported what, newest first. Route `/activity`, minimum role ADMIN (`frontend/src/router/index.js:36-41`; the guard that turns a lower role away is `frontend/src/router/index.js:108-118`). The rail shows its link only to ADMIN (`frontend/src/App.vue:60`, icon `bi-clock-history`). Overview and rules: [activity.md](activity.md); API: [activity-log-controller.md](activity-log-controller.md).

## What the user sees
- Page head "Activity", lead "Who changed or exported what, newest first. Only administrators can see this."
- A "Show" select (`#filter-type`): "All activity" plus one option per type with a plain label (`activityTypeLabel`, e.g. "Member added", "Payments exported"), and "Clear filter" when one is set. The filter works on the entries already loaded; it does not ask the server.
- A count line (`aria-live="polite"`): "Latest 50 entries", or "3 of the latest 50 entries" while filtered.
- The entries grouped by local day, newest day first, each day a ruled card (a header on `mist`): a heading ("Today, Sunday 4 October", "Saturday 3 October" for yesterday, then "Wednesday 30 September 2026" for older days, `dayHeading`) and the count ("3 entries"). Each entry: time (`HH:mm`, local), a type badge (the `ACTIVITY_TYPES` label in a `StatusBadge`: payment recorded, message sent and reminders sent are success; members and payments exported are warning; member archived, member deleted, household deleted and person deleted are danger; every other type is neutral, `activityTone`), the server's description, and the actor (the email, or "System" for the scheduled job). md and up the entry is a grid row (time 56px, badge 176px, description, actor 240px); below md it stacks: time and badge on one line, then the description, then the actor.
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
Local component `data()` (`frontend/src/views/ActivityView.vue:98-106`): `entries`, `limit` (50), `type` ("ALL" or an `ActivityType`), `loaded`, `loadError`, `loadingMore`. Computed `visible` (filtered entries), `days` (`groupByDay(visible)`) and `canShowMore`. Methods `load`, `showMore`, `time` (`HH:mm`).

Pure helpers in `frontend/src/utils/activityLog.js` (tested in `frontend/src/__tests__/utils/activityLog.test.js`): `ACTIVITY_TYPES` (value and label for every type the server records, in filter order), `activityTypeLabel(type)` (unknown types show as they came), `actorLabel(actor)` ("System" for "system" or a missing actor), `filterByType(entries, type)`, `activityTone(type)` (the badge tone), `dayHeading(date, now)`, `groupByDay(entries, now)` (consecutive entries on the same local day; entries without a readable date are left out), `nextLimit(limit)`, `PAGE_SIZE` 50, `MAX_LIMIT` 200. `ACTIVITY_TYPES` mirrors `domain/enumeration/ActivityType.java`; add a type in both places.

## Collaborators
- `frontend/src/services/api.js` (default import); `formatDate` from `frontend/src/utils/index.js`
- Components: `PageHead`, `AlertBanner`, `BaseButton`, `EmptyNote`, `StatusBadge`, `TextButton`

## Side effects
None; the screen only reads.

## Gotchas
- Days are cut at local midnight, so an entry near midnight can land on the other day if the browser and the server are in different zones. The time is the server's local clock as written (the API sends a date-time without a zone); the browser reads it as local time, which is right when both are in the same zone.
- The filter only sees what is loaded: pick a type, then "Show more" to look further back.
- The latest 200 entries are the most the screen can show; older ones are only in the database.

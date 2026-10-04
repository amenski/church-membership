# People

A **person** is anyone on the register: a member or a dependent with no membership (a child, a spouse who pays no dues, a visitor not yet counted). A person's own fields are name, optional email, phone, birth date and household; the membership is separate and optional. This page covers the API (step 11 of the [person plan](../person-membership-plan.md)); the screen is not built yet.

Dues, reminders, messages, payments, member exports and every dashboard count stay on **memberships** (decision f: dues are per membership, a household never pays once). A person with no membership appears in none of them, and a person with a membership appears through it — so a dependent never leaks into a members-only number.

## Who can do what
Roles from `@PreAuthorize`; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Endpoint |
|------|--------------|----------|
| List people (all, or only dependents) | VOLUNTEER | `GET /api/people[?withoutMembership=true]` |
| Open a person | VOLUNTEER | `GET /api/people/{id}` |
| Add a person with no membership | STAFF | `POST /api/people` |
| Edit a person | STAFF | `PUT /api/people/{id}` |
| Turn a person into a member | STAFF | `POST /api/people/{id}/membership` |
| Delete a person with no membership | ADMIN | `DELETE /api/people/{id}` |

## How it works
1. `GET /api/people` returns every person whose membership is not archived, members and dependents together, by name. `?withoutMembership=true` returns only the people with no membership at all — the dependents screen.
2. Each row carries the membership read-only: `memberId` and `memberStatus` are `null` for a dependent, set for a member. `householdId` and `householdName` are read-only too.
3. `POST /api/people` creates a person with **no** membership. The Members list is untouched: a new person shows up there only after `POST /api/people/{id}/membership`.
4. `PUT` edits name, email, phone, birth date and household. For a person who has a membership the legacy member columns are kept in step in the same transaction (the dual-write used everywhere until step 12), so the drift query stays at 0.
5. `POST /api/people/{id}/membership` starts the membership with the same rules as `POST /api/members` (MEMBER or INACTIVE, counters zero, join date today unless given) and links it to the existing person. Dues, reminders, messages and payments start for them at once.
6. `DELETE /api/people/{id}` removes a person **only when they have no membership**. A member — archived or not — must first have the membership archived, or deleted permanently if it was made by mistake, from the member screens. The person's row goes last, so no payment or delivery is ever touched from here.
7. A person whose membership is ARCHIVED is hidden from everybody but an ADMIN, exactly as on the member endpoints.

## API contract
`PersonResponse` — every read, create, update and membership call returns this shape:

```json
{"id": 12, "name": "Selam Kebede", "email": null, "phone": null, "birthDate": "2015-04-01",
 "householdId": 7, "householdName": "Kebede family", "memberId": null, "memberStatus": null}
```

Request body of `POST` and `PUT /api/people` (`PersonRequest`):

```json
{"name": "Selam Kebede", "email": null, "phone": null, "birthDate": "2015-04-01", "householdId": 7}
```

| Field | Rule |
|-------|------|
| `name` | required, max 100 |
| `email` | optional, valid address, max 100; trimmed, blank becomes null |
| `phone` | optional, same rule as a member (pattern), max 20; blank becomes null |
| `birthDate` | optional, must be in the past |
| `householdId` | optional; see the table below |

Unknown properties are ignored. `householdId` follows the member rule exactly:

| Body | Effect |
|------|--------|
| field absent | on create: no household; on edit: the household is left as it is |
| `"householdId": 7` | the person joins household 7 |
| `"householdId": null` | on edit: the person leaves its household |
| unknown id | 400 `HOUSEHOLD_001`, field error on `householdId`, nothing saved |

Request body of `POST /api/people/{id}/membership` (`MembershipRequest`), optional as a whole:

```json
{"status": "MEMBER", "joinDate": "2026-10-04"}
```

| Field | Rule |
|-------|------|
| `status` | optional, MEMBER (default) or INACTIVE; DECEASED, TRANSFERRED and ARCHIVED are refused (400 `MEMBER_009`) |
| `joinDate` | optional, not in the future; defaults to today |

A missing body is the same as `{}` (MEMBER, today).

Errors (RFC 7807 `ProblemDetail`, rejected values never echoed):

| Status | When | Body |
|--------|------|------|
| 400 | name blank, a field too long, bad email/phone, a future birth date | `errors[{field, message}]` |
| 400 | unknown `householdId` | `code: "HOUSEHOLD_001"`, `errors[{field: "householdId", ...}]` |
| 400 | a membership `status` that a new membership cannot have | `code: "MEMBER_009"`, `errors[{field: "status", ...}]` |
| 401 / 403 | not signed in / role too low | as everywhere |
| 404 | unknown person id on `GET`, `PUT`, `DELETE`, `POST .../membership` | empty body, like members |
| 409 | `POST .../membership` when the person already has a membership | `code: "PERSON_001"` |
| 409 | `DELETE` when the person has a membership (any status) | `code: "PERSON_002"` |

## Activity log
New types (`activity_log.activity_type` is a plain `VARCHAR(50)`, so no migration): `PERSON_CREATED`
("Person Selam Kebede was added"), `PERSON_UPDATED` ("Person Selam Kebede was updated"), `PERSON_DELETED`
("Person Selam Kebede was deleted") with entity type `PERSON`, and `MEMBERSHIP_STARTED` ("Person Selam Kebede
became a member") with entity type `PERSON`. Names only: never an email, a phone or a birth date. Written best
effort, like all entries ([activity.md](activity.md)).

## Rules
- A person with no membership is not a recipient of anything: `message_delivery` still points at a membership, and the send paths, reminders, payments, member export and dashboard counts read `member` rows only.
- Email is optional and not unique: two people may share one inbox (a family).
- A person's household is theirs; a household never owns dues and has no head of household (decisions a and f).
- Deleting a person is ADMIN and only for a person with no membership; there is no cascade into payments or deliveries.
- A person is created, edited and read through this API; a membership is created, edited and archived through the member API. The two meet at `POST /api/people/{id}/membership`.

## Screen
Not built yet (step 11 frontend): an "Add person" under a household and a "Make a member" action are planned.

## Known issues
- No screen yet; the endpoints are exercised on H2 only (MySQL mode), not on MySQL and not in a browser.
- There is no automated test for the `PeopleController` contract yet.

## Related
- [members.md](members.md): memberships and the member API
- [households.md](households.md): a household now lists its people, dependents included
- [activity.md](activity.md), [../person-membership-plan.md](../person-membership-plan.md), [../architecture.md](../architecture.md)

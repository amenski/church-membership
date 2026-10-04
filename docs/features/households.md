# Households

A household groups the people of one family or one address: a name, an optional address and free notes. A member points to a household through their person (`person.household_id`); a household never owns dues (they stay per membership, decision f of the [person plan](../person-membership-plan.md)) and has no head of household (decision a). This page covers the API and the screens (step 10); the household detail also lists people without a membership and lets staff add, edit and promote them (step 11, [people.md](people.md)).

## Who can do what
Roles from `@PreAuthorize`; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Endpoint |
|------|--------------|----------|
| List households | VOLUNTEER | `GET /api/households` |
| Open a household with its members | VOLUNTEER | `GET /api/households/{id}` |
| Create a household | STAFF | `POST /api/households` |
| Edit name, address, notes | STAFF | `PUT /api/households/{id}` |
| Delete a household nobody belongs to | ADMIN | `DELETE /api/households/{id}` |
| Put a member in a household, move or remove them | STAFF | `householdId` in `POST` and `PUT /api/members` |

## How it works
1. Create the household (`POST /api/households`), then set `householdId` on each member (create or edit). The household's people are always read through the person, so the member list, the member JSON and the household detail agree.
2. `GET /api/households` returns every household by name with a `memberCount` (memberships) and a `personCount` (everyone, dependents included). `GET /api/households/{id}` returns the fields plus its `members` (`id`, `name`, `status`) and its `people` (every person, `id`, `name`, `birthDate`, `memberStatus`; `memberStatus` is null for a dependent).
3. Archived members stay in the household but are flagged `status: "ARCHIVED"` and are shown to an ADMIN only (`ArchivedVisibility`): for everyone else they are left out of the detail (both `members` and `people`) and of `memberCount` and `personCount`.
4. `DELETE` removes a household only when no person is assigned to it (archived members count). Otherwise it answers 409 `HOUSEHOLD_002` and nothing changes: people are never deleted, moved or cascaded. Unassign them first (`householdId: null`).
5. The database also keeps `person.household_id` as a foreign key (`ON DELETE SET NULL`, migration 012), but the use case refuses the delete first, so the rule never relies on it.

## API contract
Fixtures shared with the tests: `src/test/resources/contracts/household-request.json` and `household-response.json`.

Request body of `POST` and `PUT /api/households` (`HouseholdRequest`):

```json
{"name": "Kebede family", "addressLine1": "Via Roma 1", "addressLine2": "Scala B", "city": "Roma", "postalCode": "00100", "notes": "Prefers calls after 18:00"}
```

| Field | Rule |
|-------|------|
| `name` | required, trimmed, max 100 |
| `addressLine1`, `addressLine2`, `city` | optional, max 100 |
| `postalCode` | optional, max 20 |
| `notes` | optional, max 2000 |

Optional text is trimmed and a blank value is stored as null. A `PUT` replaces every field (a missing optional field is cleared). Unknown properties (for example `members`) are ignored.

Response of `GET /api/households/{id}`, `POST` and `PUT` (200; `members` and `people` are `[]` right after create):

```json
{"id": 7, "name": "Kebede family", "addressLine1": "Via Roma 1", "addressLine2": "Scala B", "city": "Roma", "postalCode": "00100", "notes": "Prefers calls after 18:00",
 "members": [{"id": 1, "name": "Abebe Kebede", "status": "MEMBER"}, {"id": 2, "name": "Tigist Kebede", "status": "INACTIVE"}],
 "people": [{"id": 1, "name": "Abebe Kebede", "birthDate": null, "memberStatus": "MEMBER"}, {"id": 2, "name": "Tigist Kebede", "birthDate": null, "memberStatus": "INACTIVE"}, {"id": 3, "name": "Selam Kebede", "birthDate": "2015-04-01", "memberStatus": null}]}
```

`people` is every person of the household (members and dependents), `members` is the memberships only. Both are kept in the JSON: the members table reads `members`, the [people](people.md) work reads `people`.

Response of `GET /api/households`:

```json
[{"id": 7, "name": "Kebede family", "city": "Roma", "memberCount": 2, "personCount": 3}]
```

Member JSON gains two nullable fields, read from the person (`householdName` is read-only): `"householdId": 7, "householdName": "Kebede family"`. They also appear on the member embedded in a payment; a delivery's recipient summary does not carry them.

`MemberRequest` accepts an optional `householdId`:

| Body | Effect |
|------|--------|
| field absent | on create: no household; on edit: the household is left as it is (so "mark inactive" does not clear it) |
| `"householdId": 7` | the person joins household 7 |
| `"householdId": null` | on edit: the person leaves its household |
| unknown id | 400 `HOUSEHOLD_001`, field error on `householdId`, nothing saved |

Errors (RFC 7807 `ProblemDetail`, rejected values never echoed):

| Status | When | Body |
|--------|------|------|
| 400 | name blank, a field too long | `errors[{field, message}]` |
| 400 | unknown `householdId` on a member request | `code: "HOUSEHOLD_001"`, `errors[{field: "householdId", ...}]` |
| 401 / 403 | not signed in / role too low | as everywhere |
| 404 | unknown household id on `GET`, `PUT`, `DELETE` | empty body, like members |
| 409 | `DELETE` while people are assigned | `code: "HOUSEHOLD_002"` |

## Activity log
New types (`activity_log.activity_type` is a plain `VARCHAR(50)`, so no migration): `HOUSEHOLD_CREATED`, `HOUSEHOLD_UPDATED`, `HOUSEHOLD_DELETED` (entity type `HOUSEHOLD`, description "Household Kebede family was created") and `MEMBER_HOUSEHOLD_CHANGED` (entity type `MEMBER`: "Member X was added to / moved to / removed from household Y"). Names only: never an address, a note or a contact detail. Written best effort, like all entries ([activity.md](activity.md)).

## Rules
- A household is minimal: no head of household, no dues, no address validation beyond lengths.
- The members of a household are the memberships of the people whose `household_id` points to it; its people also include those with no membership (dependents, [people.md](people.md)).
- `memberCount` counts memberships and `personCount` counts people; both count only what the caller may see (archived members and their persons: ADMIN only). Since a person has at most one membership, `personCount - memberCount` is the number of dependents.
- Notes are visible to every role that can read households (VOLUNTEER and up): do not store anything sensitive in them.
- Reads join the household with the person in the same statement as the member, payment and delivery lists (explicit `join fetch`; an entity graph three levels deep did not join it).

## Screen
Code: `frontend/src/views/HouseholdsView.vue` (route `/households`, minimum role VOLUNTEER, a MEMBER is sent to their profile; "Households" sits in the rail and the mobile drawer between Members and Payments).
- Page head "Households" with "Add household" for STAFF and up. A search box ("Search by household name", client side) appears once any household exists.
- md and up: a ruled table with Name (a button that opens the household), City and Members (the `memberCount`). Below md: stacked rows with name, city and "N members".
- Empty: "No households yet. Add the first household, then choose it when you add or edit a member." with an "Add household" button (a VOLUNTEER sees "A staff member can add the first one."). A search with no hit offers "Clear search". A load failure shows a banner with "Try again".
- The detail dialog loads `GET /api/households/{id}` and shows the address, the notes (only when set) and the members, each a link to Members filtered by that name (`/members?search=...`) with their status label. Footer: Close for everyone, "Edit household" for STAFF and up, "Delete household" for ADMIN.
- Below the members the detail has "People without a membership" (dependents) with "Add person", Edit, "Make a member" and Delete (ADMIN); see [people.md](people.md#screen).
- Add and edit use one form (name, address, address line 2, city, postal code, notes with a counter). Edit sends every field, so a field cleared in the form is cleared on the server. Field errors from the server appear under their field.
- Delete asks first (`ConfirmDialog`, danger). While anyone is assigned, the server's 409 text ("This household still has people. Move them to another household or remove them from it first.") appears in the detail dialog and as a toast; nothing changes. An archived member still counts, so unassign them first.
- Members screen: the add and edit dialog has a "Household" select (None plus every household, loaded when the dialog opens). It sends `householdId` only after the user changes it (`householdTouched` in `buildMemberRequest`): choosing None on an edit sends `null`, so "Mark inactive", "Reactivate" and "Change status..." never clear the household. The household name shows under the member's name in the table and the stacked list (icon plus name, hidden when none).
- Activity: the four new types read "Household added", "Household edited", "Household deleted" and "Member household changed".

## Known issues
- The household queries ran only on H2 (MySQL mode), not on MySQL and not in a browser.
- The screens were checked by hand in a browser (admin, staff, volunteer, member; 1280 and 390 wide) but have no automated test.
- A member's household cannot be changed from the Households screen: open the member and use the Household select.

## Related
- [members.md](members.md): the `householdId` field of a member
- [people.md](people.md): the people of a household, dependents included
- [activity.md](activity.md), [../person-membership-plan.md](../person-membership-plan.md), [../architecture.md](../architecture.md)

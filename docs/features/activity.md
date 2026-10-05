# Activity log

A trail of who changed or exported member data: sign-ins, password changes, member edits, payments, messages and CSV exports. It exists because a membership register holds personal data, and without a trail nobody could say who viewed, exported or changed it. Administrators read it; nobody can edit or delete entries through the app.

## Who can do what
Roles from `@PreAuthorize`; hierarchy ADMIN > STAFF > VOLUNTEER > MEMBER.

| Task | Minimum role | Endpoint |
|------|--------------|----------|
| Read the activity log | ADMIN | `GET /api/activity-log` (`src/main/java/io/github/membertracker/infrastructure/ActivityLogController.java`) |
| Cause an entry | whoever may do the action | the member, payment, message, export, sign-in and password endpoints below |

STAFF, VOLUNTEER and MEMBER get 403 on the endpoint.

## The Activity screen
Administrators open it from "Activity" in the side menu (route `/activity`, shown only to ADMIN). It lists the latest 50 entries newest first with the time, the person (the email, or "System") and the description, can filter by type ("All activity" or one type, for example "Payment recorded") and loads 50 more at a time up to 200. Anyone else who opens the address sees the "Access denied" notice and goes to their home page. Reference: [activity-view.md](activity-view.md).

## What is recorded
Each entry has a type, a plain description, the kind and id of the record it is about, who did it (the actor) and when.

| Type | When | Example description | Recorded in |
|------|------|---------------------|-------------|
| `SIGN_IN` | a sign-in succeeds | Signed in | `AuthController.java` |
| `PASSWORD_CHANGED` | a user changes their own password | Password was changed | `UserController.java` |
| `MEMBER_CREATED` | a member is added | Member Jane Smith was added | `usecase/SaveMemberUseCase.java` |
| `MEMBER_UPDATED` | a member is edited (every save) | Member Jane Smith was updated; when the status changed: Member Jane Smith was updated, status is now TRANSFERRED | `usecase/UpdateMemberUseCase.java` |
| `MEMBER_ACTIVATED`, `MEMBER_DEACTIVATED` | the same save made a member of someone who was not one, or stopped counting a member for dues (to INACTIVE, DECEASED or TRANSFERRED) | Member Jane Smith was deactivated | `usecase/UpdateMemberUseCase.java` |
| `MEMBER_ARCHIVED` | a member is archived (the screens' "Delete" became "Archive") | Member Jane Smith was archived | `usecase/ArchiveMemberUseCase.java` |
| `MEMBER_DELETED` | a member with no history is deleted permanently (API only) | Member Jane Smith was deleted permanently | `usecase/DeleteMemberPermanentlyUseCase.java` |
| `MEMBER_HOUSEHOLD_CHANGED` | a member is put in, moved to or removed from a household | Member Jane Smith was moved to household Smith family | `usecase/SaveMemberUseCase.java`, `usecase/UpdateMemberUseCase.java` |
| `HOUSEHOLD_CREATED`, `HOUSEHOLD_UPDATED`, `HOUSEHOLD_DELETED` | a household is created, edited or deleted | Household Smith family was created | `usecase/CreateHouseholdUseCase.java`, `UpdateHouseholdUseCase.java`, `DeleteHouseholdUseCase.java` |
| `PERSON_CREATED`, `PERSON_UPDATED`, `PERSON_DELETED` | a person without a membership is added, edited or deleted | Person Sara Smith was added | `usecase/CreatePersonUseCase.java`, `UpdatePersonUseCase.java`, `DeletePersonUseCase.java` |
| `MEMBERSHIP_STARTED` | a person is made a member | Person Sara Smith became a member | `usecase/StartMembershipUseCase.java` |
| `MEMBERS_EXPORTED` | either members CSV export on the server (not "Export selected" in the Members selection bar, which is built in the browser) | Exported 11 members | `MemberController.java` |
| `PAYMENT_RECORDED` | a payment is recorded | Payment of 50.00 for 2026-10 was recorded for John Doe | `usecase/RecordPaymentUseCase.java` |
| `PAYMENTS_EXPORTED` | the payments CSV export | Exported 42 payments | `PaymentController.java` |
| `MESSAGE_SENT` | a message is sent to all, to overdue members or to one member (the monthly reminder job too) | Message "Feast day" was sent to 8 members | `usecase/SendCommunicationToAllMembersUseCase.java`, `usecase/SendCommunicationToMembersUseCase.java` |

Entries are written after the action succeeded, so a rejected request (validation error) leaves no entry. A permanent delete reads the member's name first, because it is gone afterwards.

## What is deliberately not recorded
- Emails and phone numbers: descriptions carry names only (data minimisation). The log is not a second copy of the register.
- Failed sign-ins: they are already counted and throttled by the sign-in lock ([sign-in.md](sign-in.md)), and an entry per wrong password would let anyone fill the table.
- Reads of single members, lists and the dashboard: too many, and not changes.
- Retrying a failed delivery, profile edits and the background send results: not part of this first version.

## How the actor is determined
The use cases ask a `CurrentActor` port (`src/main/java/io/github/membertracker/domain/service/CurrentActor.java`) for the signed-in user's email. The infrastructure implementation reads it from the Spring Security context (`infrastructure/security/SecurityContextCurrentActor.java`), so the domain and the use cases do not depend on Spring Security. With nobody signed in (the scheduled reminder job) the actor is `system`. A sign-in is the one case where the security context is still empty, so the controller passes the email explicitly (`RecordActivityUseCase.record(..., actorOverride)`, `usecase/RecordActivityUseCase.java`).

## Best effort
`RecordActivityUseCase` never throws: it catches every exception and logs a WARN ("Could not record activity ...", `usecase/RecordActivityUseCase.java`). A failing audit write therefore never blocks adding a member or taking a payment, and the trade is that an action can occur without an entry if the database write fails. `ActivityLogIntegrationTest` covers this.

## Rules and limits
- Newest first, `limit` 1 to 200, default 50; outside that range the answer is 400 with a field error.
- The table is `activity_log` (migration `001.schema-creation.sql`, the `actor` column and a `created_at` index added by `007.add-activity-log-actor.sql`).
- There is no retention or deletion job yet: entries accumulate forever. Deciding how long to keep them is an open item in [../todo.md](../todo.md).
- The dashboard's "recent activities" list is built from payments and messages, not from this table ([dashboard.md](dashboard.md)).

Older databases also hold three types written by the sample data (`SYSTEM_STARTUP`, `BULK_IMPORT`, `PAYMENT_REMINDER_SENT`, actor empty, shown as "System"); nothing writes them now.

Reference: [activity-log-controller.md](activity-log-controller.md), screen: [activity-view.md](activity-view.md).

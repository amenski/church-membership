# Person / Household / Membership: plan

Read at commit `2b079ee` (working tree: migration 007 present, 008 being added). Migration numbers below start at 009: run `ls src/main/resources/db/sql` first and shift them if the folder has moved on. Never edit an applied file (checksum).

Replaces the Phase 4 draft in `docs/remediation-plan.md`, which predates `MemberRequest`, the missed-month marker, `MessageTemplates`, the Tailwind frontend and the CSV exports. Closes C9 (delete erases payments) and C10 (unique required email blocks families) from `docs/functionality-audit.md:121-127`.

## 1. Why and what changes for users

- A child or a spouse without an email can be stored. Today the form refuses (`MembersView.vue:364`) and the database refuses (`001.schema-creation.sql:8`, `:19`).
- Two people can share one inbox. A message to everyone sends one email per address, not one per person.
- Deleting no longer destroys money records. "Delete" becomes "Archive"; a real delete exists only for a record made by mistake.
- Membership has a status (member, inactive, deceased, transferred, archived) instead of one on/off switch. Each status says whether dues, reminders and messages apply.
- Later: households (a family grouped, one address) and people who are not members (children) on the register.
- Unchanged: the screens, roles, the monthly dues job, payments, the delivery history, existing ids. Payments and deliveries keep pointing at the same row id. The JSON the frontend reads keeps `name`, `email`, `phone`, `active` until the frontend stops using them.

## 2. Decisions needed from the owner

Nothing blocks step 1. Each later step names the decision it needs (marked **GATE**).

**Owner decisions (October 2026): all seven recommendations accepted (a to g), and the status TRANSFERRED is added.** The statuses are five: MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED. Only MEMBER counts for dues, reminders, messages and payments (`countsForDues()` is true only for MEMBER).

| # | Question | Recommendation | Cost of the options |
|---|---|---|---|
| a | Is a household a real thing (shared address, head of household) or just a label? | Real, but minimal: `household(name, address)` and `person.household_id`. No head-of-household column yet. Built late (step 10). | Label only (`household_id` int, no table): 0 extra tables, but no shared address, no rename, no list of households. Real entity: 1 table, 1 FK, 1 small screen. Head of household: adds a rule (who is head when the head dies or moves); defer. **Owner decision: accepted.** |
| b | Person fields beyond name/email/phone? | Now: `birth_date` (tells a child from an adult, and later gates messaging). Address lives on the household, not the person. Single `name` column, no first/last split (Amharic names, today's data). Later fields (baptism date, notes, gender) are nullable columns added when a screen needs each one. | More columns now = more form, more validation, more export columns, none used. A split first/last name touches the CSV, search, sort, greeting. **Owner decision: accepted.** |
| c | Which statuses are real? | Ship `MEMBER`, `INACTIVE`, `DECEASED`, `TRANSFERRED`, `ARCHIVED` (five; the owner chose to add `TRANSFERRED`). Defer `VISITOR` and `REGULAR_ATTENDER` until the guest follow-up flow is designed (audit: "no path from first-time guest to member"). | See the rules table below. Extra statuses cost one enum value plus a rule row each, but every one must be answered for dues, reminders, messages and the filter. **Owner decision: accepted; statuses: five (TRANSFERRED added).** |
| d | What does "delete" mean? | Default is archive (status `ARCHIVED`, hidden from lists, everything kept). A separate ADMIN permanent delete only when the member has no payments and no deliveries. Payment and delivery FKs become `RESTRICT`. Erasure on request = anonymise the person (later, out of scope). | Block delete when payments exist: still erases delivery history of members without payments and leaves duplicates forever. Keep cascade: C9 stays open. Archive only, no hard delete: typos live forever. **Owner decision: accepted.** |
| e | Email rules? | Optional. Not unique in the database. Stored trimmed; MySQL 8's default collation is case-insensitive, so lookups still ignore case. No duplicate error on save. At send time, recipients are filtered to those with an email and deduped by address (first member by id wins). | Unique per person: still blocks a couple sharing an inbox. Shared allowed with a UI warning: needs a lookup endpoint and a warning state; add later if duplicates by mistake turn out to be common. Dedupe by address means the second member has no delivery row for that message. **Owner decision: accepted.** |
| f | Dues per membership or per household (a family pays once)? | **The biggest product question.** Keep dues per membership (what `payment.member_id` is today). Do not build household dues now. Interim for a family that pays once: the payer is the member; dependents are persons without a membership (step 11). | Household dues: a payment must cover several people, so `payment` gets a new owner, the missed-month counter moves to the household, reminders go once per family, reports change. Weeks of work and a new audit of every dues number. **Owner decision: accepted.** |
| g | Migration risk appetite? | Expand, dual-write for two releases, then contract (steps 7 to 12). The code writes both places inside one mapper; a drift query proves they match. One congregation under 1,000 rows makes a one-shot cutover feasible, but then the only rollback is a restore from dump. | One-shot: fewer releases, one big risky one, no test of live behaviour in between. Dual-write: about 60 extra lines in one class, removed at step 12. **Owner decision: accepted.** |

Status rules (what each status means):

| Status | Dues counter and `active` | Reminders | Messages | Listed by default | Payment can be recorded |
|---|---|---|---|---|---|
| MEMBER | yes (`active` = true) | yes | yes | yes | yes |
| INACTIVE | no, counter frozen, reactivate resets it (as `Member.activate()` does, `Member.java:75`) | no | no | yes | no |
| DECEASED | no | no | never | yes, filter | no |
| TRANSFERRED | no | no | no | yes, filter | no |
| ARCHIVED | no | no | no | no (admin filter) | no |

Today `active=false` means all of INACTIVE, TRANSFERRED and DECEASED (`docs/architecture.md:120`). The backfill maps `active=false` to INACTIVE; the owner re-labels the deceased by hand.

## 3. Target model

### Tables

Names: the table `member` stays `member` (it is the membership). A rename adds an FK and entity churn for no gain; do it never or at step 12.

| Table | Column | Type | Null | Notes |
|---|---|---|---|---|
| `person` (new) | `id` | BIGINT AUTO_INCREMENT PK | no | backfilled with `member.id` |
| | `name` | VARCHAR(100) | no | |
| | `email` | VARCHAR(100) | yes | index `idx_person_email`, NOT unique |
| | `phone` | VARCHAR(20) | yes | |
| | `birth_date` | DATE | yes | decision b |
| | `household_id` | BIGINT | yes | FK `household(id)` ON DELETE SET NULL, index |
| | `created_at`, `updated_at` | TIMESTAMP | no | as in `001.schema-creation.sql:14` |
| `household` (new, step 7) | `id`, `name` VARCHAR(100) NOT NULL, `address_line1`, `address_line2`, `city`, `postal_code` (all nullable), `notes` TEXT, timestamps | | | created empty |
| `member` (existing) | `person_id` | BIGINT | no after backfill | FK `person(id)` ON DELETE RESTRICT, UNIQUE (one membership per person) |
| | `status` | VARCHAR(20) | no, default 'MEMBER' | index `idx_member_status` |
| | `archived_at` | DATETIME | yes | |
| | `join_date`, `last_payment_date`, `consecutive_months_missed`, `last_missed_count_month` | unchanged | | stay on the membership |
| | `name`, `email`, `phone`, `active` | legacy | | dropped at step 12 |
| `payment` | `member_id` FK | | | `ON DELETE RESTRICT` from step 6 (was CASCADE, `001.schema-creation.sql:31`) |
| `message_delivery` | `recipient_id` FK | | | `RESTRICT` from step 6 (was CASCADE, `:61`); still points at `member.id` |

A person with no `member` row is a dependent or household member: no dues, no messages. A member always has a person.

### Domain

- `Member` (`domain/model/Member.java`) stays the flat object the whole app passes around (36 main files import it). It gains `personId`, `status`, `archivedAt`; later `householdId`, `birthDate`. `active` becomes derived: `isActive()` = `status.countsForDues()`; `setActive` is removed and its callers use `setStatus`.
- New `domain/enumeration/MemberStatus`: `MEMBER, INACTIVE, DECEASED, TRANSFERRED, ARCHIVED` with `countsForDues()`, `canReceiveMessages()`, `listedByDefault()`. The rules table above is its test.
- `Member.activate()`/`deactivate()` (`Member.java:75`, `:83`) keep their guards and move between MEMBER and INACTIVE only; new `archive()`.
- New `Recipients` helper (usecase package): `reachable(List<Member>)` drops null/blank emails and dedupes by lower-cased address. Used by the three send paths.
- No Person or Household domain class until step 10 (YAGNI): persistence-only until then.
- New `infrastructure/persistence/mapper/MemberPersistenceMapper`: the single place `MemberEntity` and `Member` are converted. Today there are three copies: `MemberDbRepository.java:95` and `:110`, `PaymentDbRepository.java:98` and `:113`, `MessageDeliveryDbRepository.java:70-78` (the last one copies only id, name, email, phone, active, so a delivery's `recipient` JSON has no join date or counters; the mapper must keep a `toRecipient` summary or the JSON grows, decide in step 1).

### API

| Item | Today | Target |
|---|---|---|
| `MemberRequest` (`MemberRequest.java:21-24`, `:35`) | `email` required, `active` optional boolean | `email` optional (blank = null, `@Email` only when present); `status` optional enum; `active` accepted until step 12 (ignored when `status` is present); later `householdId`, `birthDate` |
| Member JSON | id, name, email, phone, joinDate, lastPaymentDate, consecutiveMonthsMissed, lastMissedCountMonth, active | same plus `status`, `personId`, `archivedAt`; `email` may be null; `active` is read-only and derived until step 12 |
| `GET /api/members` | all rows | all except ARCHIVED; `?archived=true` (ADMIN only) returns only the archived (built in step 4) |
| `GET /api/members/active`, `/inactive` | by `active` | by status groups (same URLs, same meaning) |
| `DELETE /api/members/{id}` (`MemberController.java:113-123`) | hard delete, ADMIN | archive, ADMIN, same 200/404 |
| `DELETE /api/members/{id}/permanent` | none | ADMIN, 409 when the member has payments or deliveries |
| Duplicate email on POST/PUT | 400 `EMAIL_ALREADY_EXISTS` | no error; `emailAlreadyExists` becomes unused: delete it in step 2 |
| CSV `members.csv` (`MemberController.java:151`) | `id,name,email,phone,joinDate,active,consecutiveMonthsMissed` | append `,status`; keep `active` until step 12 (appending keeps old spreadsheets working) |
| `payment.member`, `delivery.recipient` JSON | embedded flat member | unchanged |
| Send endpoints | recipients = active members | recipients = `MEMBER` status with an email, one per address; `recipientCount` counts addresses |
| `/api/people`, `/api/households` | none | new, step 10 and 11 |

### Status lifecycle

```
MEMBER <-> INACTIVE          (activate resets the counter; deactivate freezes it)
MEMBER|INACTIVE -> DECEASED | TRANSFERRED  (terminal in the UI; admin can undo)
any -> ARCHIVED              (hidden; admin can restore to INACTIVE)
```

Restoring from ARCHIVED goes to INACTIVE, never straight to MEMBER, so a returning person is not billed for the gap.

## 4. Blast radius

Counted at `2b079ee` with grep. "All classes" counts include `User`, which also has `getEmail`/`getName`/`getPhone`.

| Item | Count |
|---|---|
| Main files that import `Member` or `MemberEntity` | 36 |
| Test files that mention `Member` | 30 (plus `src/test/resources/contracts/member-request.json`) |
| `getEmail()` all classes | 25 main, 5 test |
| `setEmail(` | 8 main, 1 test |
| `isActive()` | 9 main, 8 test |
| `setActive(` | 5 main, 12 test |
| `getName()` | 15 main, 5 test |
| `setName(` | 6 main, 3 test |
| `getPhone()` | 13 main, 8 test |
| `setPhone(` | 9 main, 2 test |
| Member-only call sites of those four fields (estimate) | about 50 in 12 main files, about 10 in 5 test files |
| `new Member(` | 41 (main and test) |
| `findByActive` / `countByActive` / `findByEmailIgnoreCase` / `existsByEmailIgnoreCase` / `deleteById` callers outside the persistence package | 7 main, about 30 test stubs (`UpdateMissingPaymentCountersUseCaseTest` 13, `SendCommunicationToAllMembersUseCaseTest` 8) |
| Copies of the entity-to-member mapping | 3 (see section 3) |
| Frontend lines reading `member.active` | 21 in 7 files |
| Frontend lines reading `member.email` | 9 in 3 files |
| Frontend unit tests touching these | 3 (`memberFilters.test.js`, `memberPayload.test.js`, `audienceCount.test.js`) |
| Docs mentioning members | 15 files: `architecture.md` (19 lines), `features/members.md` (63), `member-controller.md` (39), `members-view.md` (31), `communications.md` (42), `payment-reminders.md` (35), plus 9 smaller |

Where each lives:

| Area | Files |
|---|---|
| Persistence | `MemberEntity.java:13-30`, `MemberJpaRepository.java:10-22`, `MemberDbRepository`, `PaymentDbRepository`, `MessageDeliveryDbRepository.java:102`, `CommunicationDbRepository.java:180`, `PaymentEntity`/`MessageDeliveryEntity` (`@ManyToOne MemberEntity`) |
| Domain | `Member.java`, `MemberRepository.java:13-33`, `MemberDomainException` (`emailAlreadyExists`, `memberInactive`) |
| Use cases | `SaveMemberUseCase.java:19`, `UpdateMemberUseCase.java:25-43`, `DeleteMemberUseCase.java:19`, `RecordPaymentUseCase.java:31`, `UpdateMissingPaymentCountersUseCase.java:36`, `SendPaymentRemindersUseCase.java:30-32`, `SendCommunicationToAllMembersUseCase.java:49`, `SendCommunicationToMembersUseCase.java:56`, `GetActiveMembersUseCase`, `GetInactiveMembersUseCase`, `GetDashboardStatsUseCase.java:28-30`, `GetMembersWithMissedPaymentsUseCase`, `UseCaseConfig.java:93-109` |
| Other main | `MemberController.java:99-123,151-161`, `MemberRequest.java`, `DashboardController.java:73`, `PaymentController.java:94-95` (CSV member name), `EmailService.java:88` (`setTo(member.getEmail())`), `MessageTemplates.java:25`, `CsvUtils.escapeCsv` (already null-safe, `:31`) |
| Migrations | `001.schema-creation.sql:5-19,31,61`, `002.sample-data.sql` (10 members, 2 inactive), `005` |
| Tests that change meaning | `SaveMemberUseCaseTest.java:66`, `UpdateMemberUseCaseTest.java:52,66` (duplicate email), `DeleteMemberUseCaseTest.java:18` (`deleteById`), `MemberExportTest.java:82,101` (CSV header), `DashboardQueriesIntegrationTest.java:162`, `MemberCounterPersistenceTest.java`, `CommunicationDeliveryPersistenceTest`, `MemberTest.java:26,97,101` |
| Frontend | `memberFilters.js:12,17-18,23,43`, `memberPayload.js:11`, `audienceCount.js:7`, `MembersView.vue` (form `:147`, validation `:364`, switch `:152`, delete copy `:173`, toggle `:321,427`), `PaymentsView.vue:258`, `CommunicationsView.vue:125,214`, `Dashboard.vue:135`, `api.js:350` |

## 5. Step plan

Rules for every step: one PR, the full suite green (`./gradlew test`, `npm test` in `frontend`), app starts on the default profile, docs updated in the same PR (`update-docs`). Migration files use one statement per changeset where MySQL DDL is involved (MySQL cannot roll a failed DDL back, so a half-applied file stays half applied). Every changeset gets a `--rollback`. Keep SQL in the subset both MySQL and H2 (MODE=MySQL) accept; where they differ (marked "dialect"), say so in the file and verify on MySQL.

Migration test on H2 (used from step 2): copy the pattern of `SeedDataConsistencyTest.java:30-37`: a `ResourceDatabasePopulator` runs `001`, `002`, `005` and then the new files on `jdbc:h2:mem:...;MODE=MySQL`. Skip 003 (MySQL multi-column `ADD`), 004 and 006 (users only). Changeset comment lines are plain SQL comments to the populator, so `--rollback` text is not executed there. Assert counts and column values with plain JDBC. `002` gives 10 members (ids 1 to 10, ids 7 and 9 inactive) and payments to check.

Manual MySQL check for every migration step: section 6 (dry run on a copy of `mt-demo`).

Rollback in this repo means: run the `--rollback` statements by hand on the target, then `DELETE FROM DATABASECHANGELOG WHERE ID='<id>'`. There is no Liquibase CLI in the build (`build.gradle` has `liquibase-core` only), so `liquibase rollback` is not available. Restore from the pre-deploy dump is the real safety net.

| Step | Name | Risk | Size | GATE | Status |
|---|---|---|---|---|---|
| 1 | Extract `MemberPersistenceMapper` | low | ~5 files, ~150 lines | none | done |
| 2 | Email optional and shareable | medium | ~14 files, ~250 lines | e | done |
| 3 | Add `status` (expand, dual-write `active`) | medium | ~10 files, ~200 lines | c, g | done |
| 4 | Read by status, archived hidden | medium | ~14 files, ~200 lines | c | done |
| 5 | Archive instead of delete (C9) | medium | ~10 files, ~220 lines | d | done |
| 6 | Frontend speaks `status` | low | 8 files, ~150 lines | c | done |
| 7 | Create `person` and `household` tables, unused | low | 1 migration | a, b | done |
| 8 | Backfill person, dual-write | high | ~8 files, ~300 lines | g | todo |
| 9 | Read name/email/phone from person | medium | ~6 files, ~120 lines | none | todo |
| 10 | Households (API and UI) | medium | ~12 files, ~450 lines | a | todo |
| 11 | People without a membership | medium | ~10 files, ~350 lines | f | todo |
| 12 | Contract: drop legacy columns | high | 1 migration + ~8 files | verification | todo |

Order: steps 1 to 6 need no new tables and already deliver C10 and C9. Steps 7 to 9 are the structural move and are invisible to users. Steps 10 and 11 are the new features. Step 12 is the only destructive one, last.

### Step 1: extract `MemberPersistenceMapper`

- Goal: one mapping class, no behaviour change.
- Backend: new `infrastructure/persistence/mapper/MemberPersistenceMapper` (`toDomain(MemberEntity)`, `toEntity(Member)`, `toRecipient(MemberEntity)` for the delivery summary). Replace the private copies in `MemberDbRepository`, `PaymentDbRepository`, `MessageDeliveryDbRepository`. Keep `toRecipient` exactly as today (id, name, email, phone, active) so JSON does not change.
- Migration: none. Frontend: none. Docs: `architecture.md` layer note, one line.
- Proof: new `MemberPersistenceMapperTest` round-trips every field including `lastMissedCountMonth` and a null email; `MemberCounterPersistenceTest` (header comment: the marker "must survive both member mappers") still passes.
- Rollback: revert the PR. Data lost: none.
- Why first: every later step edits the mapping; with three copies each step edits three places.

### Step 2: email optional and shareable (C10) **GATE e**

- Goal: a member can be saved without an email; two members can share one.
- Migration `009.make-member-email-optional.sql`:
  ```sql
  -- changeset aman:member-email-drop-unique
  DROP INDEX idx_member_email ON member;           -- dialect: H2 accepts "DROP INDEX idx_member_email"; verify
  --rollback CREATE UNIQUE INDEX idx_member_email ON member(email);
  -- changeset aman:member-email-nullable
  ALTER TABLE member MODIFY email VARCHAR(100) NULL;   -- dialect: H2 MySQL mode accepts MODIFY; verify
  --rollback ALTER TABLE member MODIFY email VARCHAR(100) NOT NULL;
  -- changeset aman:member-email-lookup-index
  CREATE INDEX idx_member_email_lookup ON member(email);
  --rollback DROP INDEX idx_member_email_lookup ON member;
  ```
  If H2 rejects a statement, split that changeset with `dbms:mysql` and `dbms:h2` variants.
- Backend: `Member.java:19-20` drop `@NotBlank` on email; `MemberRequest.java:21-24` optional email, blank to null (as `setPhone` does at `:60`); `SaveMemberUseCase.java:19` and `UpdateMemberUseCase.java:25-29` drop the duplicate check; delete `emailAlreadyExists` and the repository methods `existsByEmailIgnoreCase`/`findByEmailIgnoreCase` (`MemberRepository.java:27,29`, `MemberJpaRepository.java:20,22`); new `Recipients.reachable` applied in `SendCommunicationToAllMembersUseCase.java:49`, `SendCommunicationToMembersUseCase.java:56` and via it `SendPaymentRemindersUseCase.java:40`. A single-member send (`CommunicationController` send-to-member) with no email returns 400 with a clear message, not a silent skip.
- Frontend: `MembersView.vue:364` email optional (keep the format check when filled), hint "Optional", drop the duplicate-email branch at `:379`; `audienceCount.js:7` counts distinct non-empty addresses; `memberFilters.js:12` is already null-safe.
- Tests: flip `SaveMemberUseCaseTest.java:66` and `UpdateMemberUseCaseTest.java:52,66` to "duplicate is allowed"; new `RecipientsTest` (null, blank, two members one address, case difference); `MemberContractTest` posts a body without email; update `member-request.json` consumers; `audienceCount.test.js`; migration test on H2 (insert two members with the same email and one with NULL after the migration; the pre-existing 10 rows unchanged).
- Rollback: run the `--rollback` statements. It FAILS if any row has a NULL or a duplicate email, which is the point: it tells you data now needs the relaxed rule. Data lost: none.
- Interim caveat to tell the owner: until step 11 a child saved here is a membership. Mark them INACTIVE (no dues, no messages) or they appear as "behind". Do not announce it to users as the family feature.
- Risk medium: it changes who gets messages (dedupe, null skip).

### Step 3: add `status` (expand) **GATE c, g**

- Goal: store the status; keep `active` correct beside it.
- Migration `010.add-member-status.sql`:
  ```sql
  -- changeset aman:member-status-columns
  ALTER TABLE member ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
  --rollback ALTER TABLE member DROP COLUMN status;
  -- changeset aman:member-archived-at
  ALTER TABLE member ADD COLUMN archived_at DATETIME NULL;
  --rollback ALTER TABLE member DROP COLUMN archived_at;
  -- changeset aman:member-status-backfill
  UPDATE member SET status = CASE WHEN active THEN 'MEMBER' ELSE 'INACTIVE' END;
  --rollback UPDATE member SET active = (status = 'MEMBER');
  -- changeset aman:member-status-index
  CREATE INDEX idx_member_status ON member(status);
  --rollback DROP INDEX idx_member_status ON member;
  ```
- Backend: `MemberStatus` enum; `Member.status`, `isActive()` derived, `setActive` removed (12 test call sites move to `setStatus`); `MemberEntity.status`, `archivedAt`; the mapper writes `active = status.countsForDues()` (dual-write); `MemberRequest.status` optional, `active` still accepted and mapped; `UpdateMemberUseCase.java:37-43` goes through `activate()`/`deactivate()` as now. JSON gains `status`.
- Frontend: none yet. Docs: `members.md` fields table.
- Proof: `MemberStatusTest` (the rules table); H2 migration test: ids 7 and 9 become INACTIVE, the other 8 MEMBER; `MemberTest` still passes after `setActive` removal.
- Rollback: rollback statements; the `active` column was kept in step, so none lost.
- Risk medium: `active` and `status` must never disagree; the mapper is the only writer.

### Step 4: read by status, hide archived **GATE c**

- Goal: queries and rules use status; ARCHIVED is excluded everywhere.
- Backend: `MemberRepository` replaces `findByActive(boolean)` / `countByActive(boolean)` (`:13`, `:19`) with `findByStatus`, `findDuesPaying()`, `countDuesPaying()`, `countNotArchived()`; `MemberJpaRepository.java:10-18` use `StatusIn`/`StatusNot`; callers: `GetActiveMembersUseCase`, `GetInactiveMembersUseCase`, `UpdateMissingPaymentCountersUseCase.java:36`, `SendCommunicationToAllMembersUseCase.java:49`, `GetDashboardStatsUseCase.java:28-30` (total = not archived), `findAll` excludes ARCHIVED. `RecordPaymentUseCase.java:31` checks `countsForDues()`. `SendPaymentRemindersUseCase.java:30-32` keeps its in-memory filter but on status. Fixes the known gap: `findByConsecutiveMonthsMissedGreaterThanEqual` still returns inactive rows, so filter in the query (`StatusIn(dues statuses)`).
- Frontend: none. Tests: the mock stubs (`findByActive(true)`, about 30) move to the new method names; `DashboardQueriesIntegrationTest.java:162` adds an archived member that no count includes.
- Rollback: revert the PR; the schema is unchanged. Risk medium: a wide mechanical rename; the stub count is the work.

### Step 5: archive instead of delete (C9) **GATE d**

- Migration `011.payment-delivery-fk-restrict.sql` (one drop and one add per FK, per changeset):
  ```sql
  ALTER TABLE payment DROP FOREIGN KEY fk_payment_member;           -- dialect: H2 uses DROP CONSTRAINT; verify
  ALTER TABLE payment ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE RESTRICT;
  --rollback (same two statements with ON DELETE CASCADE)
  ```
  and the same for `fk_delivery_member` (`001.schema-creation.sql:61`). From now on the database itself refuses to erase payments.
- Backend: `DeleteMemberUseCase` becomes `ArchiveMemberUseCase` (status ARCHIVED, `archived_at`, `save`; never `deleteById`); new `DeleteMemberPermanentlyUseCase` (`ADMIN`, refuses with a domain exception when payments or deliveries exist; needs `PaymentRepository.existsByMember`-style and a delivery count); `MemberController.java:113-123` archives, new `DELETE /{id}/permanent`; `@PreAuthorize` stays ADMIN; add both to `RoleAuthorizationTest`.
- Frontend: `MembersView.vue:171-177` modal and `:421`/`:417` toasts say "Archive"; copy "Hides {name} from the lists. Payments and messages are kept."; `api.js:350` unchanged. Permanent delete UI waits; it is an API for now.
- Tests: rewrite `DeleteMemberUseCaseTest.java:18` to assert `save` called and `deleteById` never; new test that a permanent delete with a payment returns 409; H2 migration test inserts a payment and asserts a raw `DELETE FROM member` now fails.
- Rollback: restore the CASCADE FKs by hand. Archived rows stay archived (status is data, not schema). Data lost: none.
- Risk medium: the FK change fails if a deploy ever hard-deletes in a migration; the plan has none.

### Step 6: frontend speaks `status`

- Goal: the UI stops reading `active`.
- Frontend: `memberPayload.js:11` sends `status`; `MembersView.vue`: replace the on/off switch (`:152`) with a status select (the four values; ARCHIVED only through the menu), filter dropdown by status, `StatusLabel` from status; `memberFilters.js:17-18,23,43` use a `countsForDues(member)` helper; `audienceCount.js:7`, `PaymentsView.vue:258`, `CommunicationsView.vue:214`, `Dashboard.vue:135` call the same helper (one source for the rule); a small `utils/memberStatus.js` with labels and the rule.
- Backend: none (`active` still in JSON, now unused by the UI).
- Tests: `memberFilters.test.js`, `memberPayload.test.js`, `audienceCount.test.js`; update `member-request.json` to carry `status`; run the contract test pair.
- Rollback: revert. Risk low. This step is the last place `active` is read; after it, nothing in `frontend/src` mentions `.active` (grep proves it).

### Step 7: create `person` and `household`, unused **GATE a, b**

- Migration `012.create-person-household.sql`: `CREATE TABLE household (...)`, `CREATE TABLE person (...)` per section 3, indexes `idx_person_email`, `idx_person_household`; `--rollback DROP TABLE person; DROP TABLE household;` (create household first, drop it last).
- No code. Proof: H2 migration test asserts the tables exist and are empty; the app boots. Rollback: drop both tables; nothing references them. Risk low.

### Step 8: backfill person and dual-write

- Migration `013.backfill-person-from-member.sql`:
  ```sql
  -- changeset aman:person-backfill
  INSERT INTO person (id, name, email, phone, created_at)
    SELECT id, name, email, phone, created_at FROM member;
  --rollback DELETE FROM person;
  -- changeset aman:member-person-id
  ALTER TABLE member ADD COLUMN person_id BIGINT NULL;
  --rollback ALTER TABLE member DROP COLUMN person_id;
  -- changeset aman:member-person-link
  UPDATE member SET person_id = id;
  --rollback UPDATE member SET person_id = NULL;
  -- changeset aman:member-person-constraints
  ALTER TABLE member MODIFY person_id BIGINT NOT NULL;
  ALTER TABLE member ADD CONSTRAINT fk_member_person FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT;
  CREATE UNIQUE INDEX idx_member_person ON member(person_id);
  --rollback ALTER TABLE member DROP FOREIGN KEY fk_member_person; DROP INDEX idx_member_person ON member; ALTER TABLE member MODIFY person_id BIGINT NULL;
  ```
  Ids are preserved: `person.id = member.id`, so no payment or delivery row changes. MySQL InnoDB raises its AUTO_INCREMENT counter past an explicit id; check it (section 6). H2 may not: in tests create new rows after the backfill through the app, not SQL.
- Backend: `PersonEntity`, `PersonJpaRepository`; `MemberEntity.person` as `@OneToOne(optional = false, cascade = ALL, fetch = EAGER)` on `person_id`; add `@EntityGraph`/join so `findAll` is one query, not 1 + N; the mapper writes name/email/phone to both `member` and `person` (dual-write); reads still from `member`. A new member creates its person first, in the same transaction (`@Transactional` on the repository `save`).
- Tests: `MemberCounterPersistenceTest`-style test: save a member, assert the person row exists with the same ids and values; update it, assert both change; H2 migration test (counts equal, `person_id = id`, no NULL).
- Rollback: the rollback statements in reverse; person rows are copies, nothing lost.
- Risk high: the first time a second table backs the member. The drift query (section 6) is the check; run it after the deploy and keep it in the runbook.

### Step 9: read from person

- Goal: `person` is the source of truth; `member.name/email/phone` are only still written.
- Backend: mapper `toDomain` reads name/email/phone from `person`; sort `...OrderByConsecutiveMonthsMissedDescNameAscIdAsc` (`MemberJpaRepository.java:18`) becomes `...PersonNameAscIdAsc`; `toRecipient` reads person. No JSON change.
- Proof: the drift query returns 0 on the dry-run copy and on the real database for at least one release before step 12; all existing tests pass unchanged (they only see `Member`).
- Rollback: revert (the legacy columns are still current). Risk medium.

### Step 10: households **GATE a**

- Backend: `Household` domain class and entity, `HouseholdUseCase`s (create, rename, set address, add/remove person), `HouseholdController` (`/api/households`, STAFF to write, VOLUNTEER to read), `Member.householdId` read from `person.household_id` and returned in JSON; `MemberRequest.householdId` optional.
- Frontend: Members form gets a "Household" select (name, create-new inline); a Household column or group heading on the list; no household page yet.
- Migration: none (tables exist since step 7). Tests: controller contract test with a new fixture `household-request.json`; use case tests. Docs: new `docs/features/households.md`.
- Rollback: revert; `household_id` stays NULL-able data. Risk medium (new surface). Split in two PRs if it passes ~400 lines.

### Step 11: people without a membership **GATE f**

- Backend: `PersonController` (`/api/people`): create a person (name, optional email/phone/birthDate/household) with no `member` row; `POST /api/people/{id}/membership` creates the membership (status MEMBER or INACTIVE); the Members list stays memberships only.
- Frontend: "Add person" under a household, a "People" list or the household detail; "Make a member" action.
- Migration: none. Dues: none for these people (decision f). Messages: not recipients (`message_delivery` still points at `member`).
- Rollback: revert. Data: person rows without a membership stay harmless. Risk medium.

### Step 12: contract (destructive) **GATE: verification**

Preconditions, all true: drift query = 0 for at least one full release; a fresh dump taken within the hour; nothing reads `member.name/email/phone/active` (grep `MemberEntity` getters); the dry-run (section 6) passed on a current copy.

- Migration `014.drop-legacy-member-columns.sql`:
  ```sql
  -- changeset aman:drop-member-legacy-columns
  ALTER TABLE member DROP COLUMN name;
  ALTER TABLE member DROP COLUMN email;       -- drops idx_member_email_lookup with it
  ALTER TABLE member DROP COLUMN phone;
  ALTER TABLE member DROP COLUMN active;
  --rollback ALTER TABLE member ADD COLUMN name VARCHAR(100) NULL; ADD email VARCHAR(100) NULL; ADD phone VARCHAR(20) NULL; ADD active BOOLEAN DEFAULT TRUE;
  --rollback UPDATE member m JOIN person p ON p.id = m.person_id SET m.name = p.name, m.email = p.email, m.phone = p.phone, m.active = (m.status = 'MEMBER');
  ```
  (one DROP per changeset when running for real). The rollback restores the values from `person`, so nothing is lost; only the NOT NULL on `name` is not restored.
- Backend: remove the dual-write and `active` from `MemberEntity`; remove `Member.active` from JSON and the CSV `active` column (`MemberExportTest.java:82,101` update); remove `MemberRequest.active`.
- Docs: `architecture.md`, `members.md`, `member-controller.md`, `members-view.md`, `communications.md`, `payment-reminders.md`, a decision row in `architecture.md`.
- Rollback: hand-run the rollback, or restore the dump. Risk high; size small.

## 6. Data migration and verification

Dry run, for every migration step, on a copy of the demo database (the app never touches `felege_selam` itself). Container `mt-demo`, root password `password` (`docs/development.md:83`):

```bash
mkdir -p /tmp/mt-dry && cd /tmp/mt-dry
# 1. dump and copy
docker exec mt-demo mysqldump -uroot -ppassword --single-transaction --routines felege_selam > before.sql
docker exec mt-demo mysql -uroot -ppassword -e "DROP DATABASE IF EXISTS felege_selam_dry; CREATE DATABASE felege_selam_dry"
docker exec -i mt-demo mysql -uroot -ppassword felege_selam_dry < before.sql
# 2. snapshot the numbers
docker exec mt-demo mysql -uroot -ppassword felege_selam_dry -N -e \
 "SELECT 'members',COUNT(*) FROM member UNION ALL SELECT 'payments',COUNT(*) FROM payment UNION ALL SELECT 'deliveries',COUNT(*) FROM message_delivery UNION ALL SELECT 'sum',SUM(amount) FROM payment" > counts-before.txt
docker exec mt-demo mysqldump -uroot -ppassword --no-create-info felege_selam_dry payment message_delivery | md5 > money-before.md5   # md5sum on Linux
# 3. run the new build on the copy (Liquibase runs at start)
cd <repo> && ./gradlew bootRun --args='--spring.profiles.active=dev --spring.datasource.url=jdbc:mysql://localhost:3306/felege_selam_dry?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
# 4. snapshot again and compare
docker exec mt-demo mysql ... > counts-after.txt ; diff counts-before.txt counts-after.txt   # member/payment/delivery counts and sum equal
docker exec mt-demo mysqldump ... payment message_delivery | md5 > money-after.md5 ; diff money-before.md5 money-after.md5
```

Checks (all must return 0 unless stated):

```sql
-- orphans
SELECT COUNT(*) FROM member m LEFT JOIN person p ON p.id = m.person_id WHERE p.id IS NULL;
SELECT COUNT(*) FROM payment x LEFT JOIN member m ON m.id = x.member_id WHERE m.id IS NULL;
SELECT COUNT(*) FROM message_delivery d LEFT JOIN member m ON m.id = d.recipient_id WHERE m.id IS NULL;
-- ids preserved
SELECT COUNT(*) FROM member WHERE person_id <> id;                       -- only valid for backfilled rows; ignore for rows added after step 8
-- drift between legacy columns and person (steps 8 to 11)
SELECT COUNT(*) FROM member m JOIN person p ON p.id = m.person_id
 WHERE NOT (m.name <=> p.name AND m.email <=> p.email AND m.phone <=> p.phone);
-- status derived from active (steps 3 to 11)
SELECT status, active, COUNT(*) FROM member GROUP BY status, active;     -- MEMBER/1 and INACTIVE/0 only
-- auto-increment moved past the backfill
SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='felege_selam_dry' AND TABLE_NAME='person';   -- > MAX(member.id)
-- changesets applied
SELECT ID, DATEEXECUTED FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED DESC LIMIT 10;
```

Rollback rehearsal on the copy: run the `--rollback` statements of the step in reverse, delete its `DATABASECHANGELOG` rows, re-run the checks, then start the previous build against the copy. Do it once for steps 2, 8 and 12.

Real deploy runbook: dump the real database to a dated file first (same `mysqldump`), deploy, run the check queries, keep the dump until the next step has shipped. Liquibase runs at app start, so the migration and the new code arrive together; there is no window in which old code meets the new schema (single instance).

## 7. Risks, ranked

| # | Risk | Cheapest mitigation |
|---|---|---|
| 1 | `member` and `person` drift apart during dual-write (steps 8 to 11) | Mapper is the only writer; drift query after every deploy; a test that updates a member and reads both rows |
| 2 | Step 12 drops data that was not copied (e.g. email of a row created between checks) | Preconditions list; dump within the hour; rollback restores from `person` |
| 3 | MySQL DDL half-applied (not transactional) | One statement per changeset; dry run on a copy first; dump before every deploy |
| 4 | H2 passes, MySQL fails (dialect: `MODIFY`, `DROP FOREIGN KEY`, `DROP INDEX ... ON`) | Always run section 6 on MySQL; mark dialect lines in the SQL; split with `dbms:` when needed |
| 5 | Wrong recipients: null email, shared inbox, dedupe picks the wrong member | `RecipientsTest`; audience count shown in the UI is computed by the same rule (`audienceCount.js:7`); dry send with email disabled (`EmailService` logs "disabled") and compare the delivery rows |
| 6 | JSON contract break (frontend caches, old CSV spreadsheets) | Additive changes only until step 12; contract fixtures; append the CSV column |
| 7 | Children saved at step 2 appear as overdue | Document the INACTIVE interim; do not announce the feature until step 11 |
| 8 | Product rules change mid-flight (dues for families, statuses) | Decisions c, d, f before the steps that need them; household dues out of scope |
| 9 | Archived members disappear and a returning person is re-added as a duplicate | Admin `includeArchived` filter and restore action in step 5's follow-up; list search covers archived for ADMIN |
| 10 | N+1 queries after joining `person` | `@EntityGraph`; `findAll` query count asserted in a persistence test (under 1,000 rows it is not a performance risk once joined) |
| 11 | Migration numbers collide with parallel work | `ls db/sql` before numbering; never edit applied files |
| 12 | Scope creep into household UI | Steps 10 and 11 are separate, gated, optional after step 9 |

## 8. What to do first on Monday

Step 1, the mapper extraction. It needs no decision and no migration, and it shrinks every later step.

Brief for a Sonnet-sized agent:

1. Read `MemberDbRepository.java:95-123`, `PaymentDbRepository.java:98-126`, `MessageDeliveryDbRepository.java:61-113`, `MemberCounterPersistenceTest.java`.
2. Create `infrastructure/persistence/mapper/MemberPersistenceMapper` as a Spring `@Component` (or static utility, match the repo's style) with `toDomain`, `toEntity`, `toRecipient` (the delivery summary: id, name, email, phone, active only).
3. Inject it into the three repositories and delete their private copies. Change nothing else.
4. Add `MemberPersistenceMapperTest`: round trip of all fields, null `lastMissedCountMonth`, null email, recipient summary has no join date.
5. Done when `./gradlew test` is green and `git diff --stat` shows the three repositories shrinking and one new file plus one new test. No JSON, SQL or frontend change.

In parallel, send the owner the seven questions of section 2; the answers to e and c are needed before step 2 and step 3.

## 9. Out of scope

- Household-level dues, a family paying once, split payments (decision f).
- Online giving, payment gateways, receipts by email.
- Member self-service, member logins linked to a person (`UserRole.MEMBER` is a login role, unrelated to the membership status; keep the names apart in code and docs).
- Visitor and guest follow-up pipeline (`VISITOR`, `REGULAR_ATTENDER`), attendance tracking.
- Anonymise-on-request (erasure) endpoint, data export per person.
- Birth, baptism, marriage events and any person field not listed in decision b.
- Merging duplicate persons, import from spreadsheets.
- Renaming the table `member`, changing ids to UUIDs, paging the members API (`docs/architecture.md:125` keeps filters in the browser).

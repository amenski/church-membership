# My dues

A signed-in user sees their own dues: status, the last twelve months, receipts and their details. A MEMBER lands here after sign-in. It is phone-first and uses the comfortable density (48px buttons, 16px text).

## Who can do what
| Task | Minimum role | Screen / endpoint |
|------|--------------|-------------------|
| See own dues | MEMBER (every signed-in user) | `/my-dues`, `GET /api/me/dues` |
| Edit details, change password | MEMBER | links to `/profile` ([profile.md](profile.md)) |

`/my-dues` needs sign-in only. MEMBER lands on it after sign-in and on `/`; STAFF and above keep `/dashboard`. The link "My dues" is in More only (not the rail). A MEMBER has no bottom tab bar, their phone top bar keeps the name and Sign out.

## Matching rules
The login is matched to a member by email (the owner's choice; there is no user-to-member link).
- The signed-in user's email and `person.email` are compared trimmed and case-insensitive.
- Members with status ARCHIVED never match.
- Exactly one member must match. Zero matches or more than one (two people may share an email, for example a family) both answer 404 with an empty body. The server never guesses, so a shared address cannot expose another person's dues.
- Only that member's data is returned: nothing about other members or the household's other members.

Code: `MyDuesController`, `GetMyDuesUseCase`, `MemberRepository.findNotArchivedByEmail`; payments come from the existing `PaymentRepository.findByMember`.

## API
`GET /api/me/dues`, `@PreAuthorize("hasRole('MEMBER')")`.

| Status | Body |
|--------|------|
| 200 | the object below |
| 401 | ProblemDetail, not signed in |
| 404 | empty: no member, or more than one member, has the signed-in email |

```json
{
  "memberId": 7,
  "name": "Abebe Kebede",
  "status": "MEMBER",
  "joinDate": "2023-01-15",
  "monthsBehind": 0,
  "lastPaymentDate": "2026-10-04",
  "householdName": "Kebede household",
  "email": "abebe@example.com",
  "phone": "+251912345601",
  "paidMonths": ["2025-11", "2025-12", "2026-10"],
  "payments": [
    { "receiptNumber": "R-000034", "period": "2026-10", "paymentDate": "2026-10-04", "method": "CASH", "amount": 25.0 }
  ]
}
```
- `monthsBehind` is the member's `consecutiveMonthsMissed`. `householdName` is null without a household.
- `paidMonths`: the distinct billing periods ("yyyy-MM") paid within the last 12 months (current month and the 11 before), oldest first.
- `payments`: the latest 12, newest payment date first then newest id. `receiptNumber` is "R-" plus the payment id padded to six digits, the same as the payments screen. `method` is the payment method code.

## Screen
1. Greeting: the name and "Member since <month year>".
2. Status card: "You are N months behind" when `monthsBehind` is above 0, else "You are paid up through <month>" (the current month if paid, otherwise last month); a non-MEMBER status reads "Your membership is inactive" and so on. The sentence under it names the last payment and what is due. The rule is the year strip's (`frontend/src/utils/yearStrip.js`, `utils/dues.js`), wording in `utils/myDues.js`.
3. "Your year": the large `YearStrip` with month initials.
4. Receipts: period, receipt number, method, amount. There is no Download link: the receipt PDF exists only inside the payments screen's dialog and is not reusable from here.
5. Your details: phone, email, household; "Edit my details" and "Change password" (both open `/profile`) and "Sign out".
6. No match, or 404: "We could not find your membership. Ask the church office to check that your sign-in email matches the email they have for you." with a "Go to Profile" button. Any other failure shows a banner with "Try again".

## Known issues
- The match is by email; an explicit user-to-member link is the follow-up if emails turn out to be unreliable (see [../todo.md](../todo.md)).
- Test: `src/test/java/io/github/membertracker/MyDuesIsolationIntegrationTest.java` covers isolation, 404 cases, 401 and a STAFF user. No frontend tests for this screen.

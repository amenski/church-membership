# MemberTracker

Membership and monthly-dues tracking for a church: members, payments, and email to members. Spring Boot backend with a Vue 3 frontend.

## Quick start

```bash
./gradlew bootRun                           # backend on :8080 (needs MySQL, see docs)
cd frontend && npm install && npm run dev   # frontend on http://localhost:3000
```

`bootRun` uses the `dev` profile automatically; a production JAR needs `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` in the environment.

Full setup, build and deployment steps are in [docs/development.md](docs/development.md).

## Documentation

**Guides** (kept current with the code)

| Doc | Read it when… |
|-----|---------------|
| [docs/development.md](docs/development.md) | Setting up, running, building or deploying |
| [docs/architecture.md](docs/architecture.md) | Deciding where new code goes; checking past design decisions |
| [docs/authentication.md](docs/authentication.md) | Working on login, sessions, cookies, roles or permissions |
| [docs/email.md](docs/email.md) | Configuring SMTP or changing how messages go out |

### Features

Overviews explain a feature to people (who can do what, how it works, rules, known issues); the Backend/Frontend docs are the technical reference per entry point (endpoints, state, actions, errors, gotchas).

| Feature | Overview | Backend | Frontend |
|---------|----------|---------|----------|
| Sign-in and sessions | [sign-in](docs/features/sign-in.md) | [auth-controller](docs/features/auth-controller.md) | [login-view](docs/features/login-view.md) |
| Profile and password | [profile](docs/features/profile.md) | [user-controller](docs/features/user-controller.md) | [profile-view](docs/features/profile-view.md) |
| Members | [members](docs/features/members.md) | [member-controller](docs/features/member-controller.md) | [members-view](docs/features/members-view.md) |
| Households | [households](docs/features/households.md) | same page | same page (Screen) |
| People (members and dependents) | [people](docs/features/people.md) | same page | none yet (step 11 pending) |
| Payments | [payments](docs/features/payments.md) | [payment-controller](docs/features/payment-controller.md) | [payments-view](docs/features/payments-view.md) |
| Communications | [communications](docs/features/communications.md) | [communication-controller](docs/features/communication-controller.md) | [communications-view](docs/features/communications-view.md) |
| Activity log | [activity](docs/features/activity.md) | [activity-log-controller](docs/features/activity-log-controller.md) | [activity-view](docs/features/activity-view.md) |
| Dashboard | [dashboard](docs/features/dashboard.md) | [dashboard-controller](docs/features/dashboard-controller.md) | [dashboard-view](docs/features/dashboard-view.md) |
| Payment reminders (scheduled) | [payment-reminders](docs/features/payment-reminders.md) | [payment-reminder-scheduler](docs/features/payment-reminder-scheduler.md) | none |

**Planning**

| Doc | What it is |
|-----|------------|
| [docs/todo.md](docs/todo.md) | Live backlog |
| [docs/functionality-audit.md](docs/functionality-audit.md) | Audit from 3 October 2026: maturity scores, critical defects (C1–C10), roadmap |
| [docs/role-auth-checkpoints.md](docs/role-auth-checkpoints.md) | Progress tracker for the `feature/role-auth` branch |

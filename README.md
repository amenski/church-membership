# MemberTracker

Membership and monthly-dues tracking for a church: members, payments, and email to members. Spring Boot backend with a Vue 3 frontend.

## Quick start

```bash
./gradlew bootRun                           # backend on :8080 (needs MySQL, see docs)
cd frontend && npm install && npm run dev   # frontend on http://localhost:3000
```

Full setup, build and deployment steps are in [docs/development.md](docs/development.md).

## Documentation

**Guides** (kept current with the code)

| Doc | Read it when… |
|-----|---------------|
| [docs/development.md](docs/development.md) | Setting up, running, building or deploying |
| [docs/architecture.md](docs/architecture.md) | Deciding where new code goes; checking past design decisions |
| [docs/authentication.md](docs/authentication.md) | Working on login, sessions, cookies, roles or permissions |
| [docs/email.md](docs/email.md) | Configuring SMTP or changing how messages go out |

**Planning**

| Doc | What it is |
|-----|------------|
| [docs/todo.md](docs/todo.md) | Live backlog |
| [docs/functionality-audit.md](docs/functionality-audit.md) | Audit from 3 October 2026: maturity scores, critical defects (C1–C10), roadmap |
| [docs/role-auth-checkpoints.md](docs/role-auth-checkpoints.md) | Progress tracker for the `feature/role-auth` branch |

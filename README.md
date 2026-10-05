# MemberTracker

Membership and monthly-dues tracking for a church: members, payments, and email to members. Spring Boot backend with a Vue 3 frontend.

## Quick start

```bash
docker compose up -d --wait                 # MySQL on :3306, data kept in ./data/mysql
./gradlew bootRun                           # backend on :8080
cd frontend && npm install && npm run dev   # frontend on http://localhost:3000
```

`bootRun` uses the `dev` profile automatically, which loads sample data (members, payments, messages and two sample users). A production JAR starts with an empty database and needs `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` in the environment, plus `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` for the first start to create the first administrator ([First start](docs/development.md#first-start)).

`docker-compose.yml` runs only the database, with the dev profile's credentials; copy `.env.example` to `.env` to change them. Without Docker, any MySQL 8 with a `felege_selam` database works.

Self-registration is disabled and the sample users' password hashes do not match the passwords in their comments, so give one a password first: [docs/development.md](docs/development.md#create-a-login). Full setup and build steps are in the same file. To run it on a Linux machine of your own, follow [docs/deploy-linux.md](docs/deploy-linux.md): the recommended way if the machine already runs Docker is `docker-compose.server.yml`, which runs the app (built from the `Dockerfile`) and MySQL together.

## Documentation

**Guides** (checked against the code on 5 October 2026)

| Doc | Read it when… |
|-----|---------------|
| [docs/development.md](docs/development.md) | Setting up, running or building; the deployment reference (variables, Caddy, systemd, production checklist) |
| [docs/deploy-linux.md](docs/deploy-linux.md) | Putting MemberTracker on one Linux machine, in Docker or as a jar under systemd: install, database, HTTPS, firewall, backups, updates |
| [docs/architecture.md](docs/architecture.md) | Deciding where new code goes; checking past design decisions |
| [docs/design.md](docs/design.md) | Building or restyling a screen: tokens, density, layout and components |
| [docs/authentication.md](docs/authentication.md) | Working on login, sessions, cookies, roles or permissions |
| [docs/email.md](docs/email.md) | Configuring SMTP or changing how messages go out |

### Features

Overviews explain a feature to people (who can do what, how it works, rules, known issues); the Backend/Frontend docs are the technical reference per entry point (endpoints, state, actions, errors, gotchas).

| Feature | Overview | Backend | Frontend |
|---------|----------|---------|----------|
| Sign-in and sessions | [sign-in](docs/features/sign-in.md) | [auth-controller](docs/features/auth-controller.md) | [login-view](docs/features/login-view.md) |
| More (phone menu) | [more-view](docs/features/more-view.md) | none | [more-view](docs/features/more-view.md) |
| My dues (member self-service) | [my-dues](docs/features/my-dues.md) | same page | same page |
| Profile and password | [profile](docs/features/profile.md) | [user-controller](docs/features/user-controller.md) | [profile-view](docs/features/profile-view.md) |
| Members | [members](docs/features/members.md) | [member-controller](docs/features/member-controller.md) | [members-view](docs/features/members-view.md) |
| Member detail page | [member-detail-view](docs/features/member-detail-view.md) | same page | same page |
| Households | [households](docs/features/households.md) | same page | same page (Screen) |
| People (members and dependents) | [people](docs/features/people.md) | same page | the people list on the household detail ([households](docs/features/households.md)) |
| Payments | [payments](docs/features/payments.md) | [payment-controller](docs/features/payment-controller.md) | [payments-view](docs/features/payments-view.md) |
| Communications | [communications](docs/features/communications.md) | [communication-controller](docs/features/communication-controller.md) | [communications-view](docs/features/communications-view.md) |
| Activity log | [activity](docs/features/activity.md) | [activity-log-controller](docs/features/activity-log-controller.md) | [activity-view](docs/features/activity-view.md) |
| Dashboard | [dashboard](docs/features/dashboard.md) | [dashboard-controller](docs/features/dashboard-controller.md) | [dashboard-view](docs/features/dashboard-view.md) |
| Payment reminders (scheduled) | [payment-reminders](docs/features/payment-reminders.md) | [payment-reminder-scheduler](docs/features/payment-reminder-scheduler.md) | none |

**Planning**

| Doc | What it is |
|-----|------------|
| [docs/todo.md](docs/todo.md) | The open work: decisions to make, small leftovers, items the owner deferred |
| [docs/functionality-audit.md](docs/functionality-audit.md) | Audit of 3 October 2026 (maturity scores, defects C1 to C10, roadmap) with a status table that says what is fixed |

**Archive** (finished plans, kept for history in [docs/archive/](docs/archive/))

| Doc | What it was |
|-----|-------------|
| [docs/archive/person-membership-plan.md](docs/archive/person-membership-plan.md) | The 12-step plan for people, households and membership status (done, migrations 009 to 014) |
| [docs/archive/design-todo.md](docs/archive/design-todo.md) | The plan for the design mockup of every page (done) |
| [docs/archive/design-gaps.md](docs/archive/design-gaps.md) | The gap list between the app and the mockup (done) |
| [docs/archive/role-auth-checkpoints.md](docs/archive/role-auth-checkpoints.md) | Progress tracker for the role-based authorization work (done) |
| [docs/archive/validation-plan.md](docs/archive/validation-plan.md) | The plan for request validation and the global exception handler (done) |

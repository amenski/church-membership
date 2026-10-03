# MemberTracker Project - Todo List

*Last updated: October 3, 2026*  
*Critical defects found in the October 2026 audit (C1–C10) are listed in [functionality-audit.md](functionality-audit.md#3-critical-gaps); fix those first.*

## 📋 Overview

This document tracks missing features, improvements, and technical debt in the MemberTracker project. Items are prioritized based on impact and readiness for production deployment.

## ✅ Recently Completed (February 2026)

### Validation & Error Handling
- [x] Added Spring Validation annotations to DTOs and domain models
- [x] Implemented `GlobalExceptionHandler` for structured error responses
- [x] Added validation for API endpoints (`@Valid`, `@Positive`, etc.)
- [x] Created validation error response format

### Java 17 Compatibility
- [x] Replaced `Executors.newVirtualThreadPerTaskExecutor()` with `Executors.newCachedThreadPool()` in `SendCommunicationTo*UseCase`
- [x] Fixed type mismatch in `MessageDeliveryDbRepository` (changed `MemberDbRepository` → `MemberJpaRepository`)

### Documentation
- [x] Validation strategy documented (now in `architecture.md`)
- [x] Docs consolidated (October 2026): `doc/` and the frontend auth docs merged into `docs/` (see `README.md` for the index)

---

## 🚨 Critical Priority (Must be done before production)

### Security
- [x] **Role-Based Authorization Enhancement**
  - Add `@PreAuthorize` annotations to all controller methods *(done: 3cf5d84)*
  - Implement fine-grained permission control (ADMIN > STAFF > VOLUNTEER > MEMBER, via `RoleHierarchy`) *(done: 3cf5d84)*
  - Add permission checks in frontend routes *(done: `requiresRole` on all staff routes; `/profile` for MEMBER)*
  - **Location**: All controller classes, frontend route guards
  - **Tracking**: `role-auth-checkpoints.md`

- [x] **HTTPS Configuration** *(done: Caddy reverse proxy, `deploy/Caddyfile`; forward headers enabled)*
  - Generate/obtain SSL certificates
  - Configure Spring Boot for HTTPS in production
  - Set up redirect from HTTP to HTTPS
  - **Location**: `application.properties`, deployment configuration

### Testing
- [ ] **Comprehensive Test Coverage**
  - [x] Domain unit tests *(daf0019)*
  - [x] Use case unit tests *(97a9e04)*
  - [ ] Frontend tests (Vitest)
  - Unit tests for all use cases (minimum 80% coverage)
  - Integration tests for all API endpoints
  - Frontend component tests (Vue.js)
  - **Location**: `src/test/`, frontend test files
  - **Tools**: JUnit 5, Spring Boot Test, Vue Test Utils

- [ ] **Test Infrastructure Setup**
  - Configure JaCoCo for code coverage reporting
  - Add test containers for database testing
  - [x] Full-context smoke tests for the dev and default profiles on H2 (catches startup failures that @WebMvcTest slices cannot)
  - Set up CI/CD pipeline with test execution
  - **Location**: `build.gradle`, GitHub Actions workflow

---

## 🔴 High Priority (Should be done soon)

### Remediation loop
- [x] Phase 0: dev profile starts; full-context smoke tests
- [x] Phase 1a: C5 token type + refresh cookie path, C6 generic login error
- [x] Phase 1b: C3 scheduling monthly + idempotent counter (changeset 005), C4 {{member_name}}
- [x] Phase 2: C8 MemberRequest DTO
- [x] Phase 3: secrets out of config, CSRF back on (activity log writes deferred: see below)
- [ ] Activity log writes: record sign-in, member create/edit/delete, exports and payments in the existing activity_log table (table exists, nothing writes to it; dashboard recent activities could then read it)
- [ ] Phase 4: Person/Membership split (needs a decision, not started)

### Bugs found by checking the docs against the code (October 2026)
- [ ] **Overdue tracking:** there is no pre-due reminder window (the unused policy code was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`; recover from git history if you build it)
- [x] **Sign-in:** locks end by themselves after 15 minutes (`locked_until`); one generic message; per-IP and per-email throttle (a permanent lock with no `locked_until` still needs a database edit)
- [x] **Profile:** changing the password ends other sessions; refresh sessions last at most 30 days from sign-in
- [ ] **Members:** there is no automatic deactivation after 3 missed months (the unused code was removed; see git history); the add form cannot set a join date
- [ ] **Payments:** no active-member check when recording; payments older than 3 months cannot be entered (no way to migrate history); the receipt does not show the paid month; the CSV export is open to VOLUNTEER (audit: restrict to STAFF)
- [ ] **Communications and dashboard:** send-to-overdue has no active-member filter; an empty overdue match still saves a communication marked as sent; no endpoint sends an existing draft; the delivery summary cards skip DELIVERED; dashboard endpoints return 200 with zeros on any exception (errors hidden); payments are loaded several times per dashboard load
- [ ] **Communications (display):** the Recipients column shows "-" for everything except send-to-all (`CommunicationsView.vue:376`); the dashboard Send Reminder is stored as an announcement, not a REMINDER
- [ ] **Dead code to delete or wire in:** `memberStore`, `paymentStore`, `communicationStore`, `RegisterView.vue` (the backend half, `ProcessMemberPaymentUseCase`, `RegisterUserUseCase` and the policy, was removed in `chore: remove unused use cases, the membership policy and PhoneNumber`)

### Bugs found by tests (October 2026)
- [x] Password change always failed (bcrypt hash checked against the strength rule) *(f26514e)*
- [x] Reactivation on payment was never saved *(fe2eac5)*
- [x] Duplicate payment for the same member and month accepted *(fe2eac5)*
- [x] "Send to all" included inactive members *(1e0eb4a)*
- [x] Reminder skipped exactly 7 days before the due date *(6b31097)*
- [x] Minimum-amount message said 10.0 while 0.01 was accepted *(fe2eac5)*
- [x] Audit C2–C4 are pinned by @Disabled tests (all three are fixed and their tests enabled; see functionality-audit.md)
- [ ] Open questions: `RecordPaymentUseCase` accepts payments for inactive members

### Core Features
- [x] **Frontend Delivery Status Display**
  - [x] Add delivery status column in communications table (delivery dialog in `CommunicationsView.vue`)
  - [x] Implement status summary cards (sent / failed / pending)
  - [x] Add retry functionality for failed deliveries
  - **Location**: `frontend/src/views/CommunicationsView.vue`

- [ ] **Email Retry Logic Enhancement**
  - [x] Implement exponential backoff retry strategy (send-to-all only; `SendCommunicationToMembersUseCase` still sends without retry)
  - [x] Add retry configuration to `MailProperties` (`app.mail.retry.*`)
  - Track retry attempts in `MessageDelivery`
  - **Location**: `EmailService`, `MailProperties`, retry use case

- [x] **CSV Export Improvements**
  - Frontend blob handling unified in `downloadBlob`
  - Error notification added for failed exports
  - CSV formula-injection guard added (new, security)
  - Streaming for large datasets: dropped; exports are plain in-memory responses (UTF-8 with BOM). Exports load all rows in memory, which is fine for a congregation under ~1,000 people (see functionality-audit.md assumptions).
  - **Location**: `MemberController`, `PaymentController`, frontend views

### Code Quality
- [x] **Finish the Communication domain model**
  - [x] Add behaviour to `Communication` (e.g. `markAsSent()`, `isSent()`); it only has getters/setters
  - [x] Move `CommunicationType` from an inner enum to `domain/enumeration`
  - **Location**: `domain/model/Communication.java`

- [x] **API Documentation** *(springdoc, dev profile only)*
  - Add OpenAPI/Swagger documentation
  - Document all endpoints with request/response examples
  - Generate interactive API documentation
  - **Location**: Swagger configuration, controller annotations

- [x] **Error Handling Consistency**
  - [x] Standardize error response format (ProblemDetail)
  - [ ] Localized error messages: deferred until a second UI locale exists
  - [ ] Structured (JSON) logging: deferred to the Monitoring & Logging item (infrastructure)
  - [x] Frontend notifications for session expiry, 401 and 403 (1c13996), logged-out visitors are no longer redirected to the expired-session page
  - **Location**: `GlobalExceptionHandler`, logging configuration

---

## 🟡 Medium Priority (Important enhancements)

### Features
- [ ] **Multi-Channel Communication**
  - SMS integration (Twilio/MessageBird)
  - WhatsApp message support
  - Communication template management
  - **Location**: New service classes, template database

- [ ] **Dashboard Enhancements**
  - Add Chart.js visualizations
  - Implement advanced member analytics
  - Add export to PDF/Excel functionality
  - **Location**: `DashboardController`, frontend dashboard components

- [x] **Member Search & Filtering**
  - [x] Multi-criteria search: name, email, phone, status, payment status, join-date range, sortable columns (client-side)
  - [ ] Saved filter presets: deferred
  - [x] Export search results (POST /api/members/export with the visible ids)
  - [ ] Server-side search and pagination: deferred until a congregation above ~2,000 members or multiple campuses
  - **Location**: `MemberController`, frontend search component

### Performance
- [ ] **Database Query Optimization**
  - Add indexes for common queries
  - Implement pagination for large result sets
  - Optimize N+1 query problems
  - **Location**: Entity definitions, repository methods

- [ ] **Frontend Performance**
  - [x] html2pdf.js loaded on demand (PaymentsView chunk 990.63 kB → 8.14 kB)
  - Implement lazy loading for large datasets
  - Optimize bundle size (tree shaking)
  - Add frontend caching strategies
  - **Location**: Vue.js components, build configuration

---

## 🟢 Low Priority (Nice to have)

### Infrastructure
- [ ] **Docker Configuration**
  - Create Dockerfile for backend and frontend
  - Docker Compose for local development
  - Production deployment scripts
  - **Location**: `Dockerfile`, `docker-compose.yml`

- [ ] **Monitoring & Logging**
  - Add application health checks
  - Implement structured logging (JSON format)
  - Set up error tracking (Sentry/ELK)
  - **Location**: Logback configuration, health endpoints

- [ ] **Backup & Recovery**
  - Automated database backup procedures
  - Data export functionality
  - Disaster recovery plan
  - **Location**: Backup scripts, admin interface

### Advanced Features
- [ ] **Mobile Application**
  - React Native mobile app
  - Offline capability for member data
  - Push notifications for payments/communications
  - **Location**: New mobile project repository

- [ ] **Calendar Integration**
  - Sync events with Google Calendar
  - Schedule communications via calendar
  - Member event attendance tracking
  - **Location**: Calendar service integration

- [ ] **Payment Gateway Integration**
  - Online payment processing
  - Recurring payment support
  - Payment receipt generation
  - **Location**: Payment gateway service

---

## 📊 Progress Tracking

### Phase 1: Security & Foundation
- **Status**: 70% complete
- **Remaining**: Role-based authorization, HTTPS, comprehensive testing

### Phase 2: Enhanced Features
- **Status**: 30% complete
- **Remaining**: Multi-channel communications, dashboard improvements

### Phase 3: Production Readiness
- **Status**: 0% complete
- **Remaining**: Docker, monitoring, performance optimization

---

## 🛠️ Technical Debt

### High Impact
1. **Low test coverage** (<5%) - critical for maintainability
2. **Missing API documentation** - hinders integration
3. **Inconsistent error handling** - affects user experience

### Medium Impact
1. **Large frontend bundle size** - affects load times
2. **Database query optimization needed** - scalability concern
3. **No CI/CD pipeline** - manual deployment process

### Low Impact
1. **Code quality tools missing** (SonarQube, pre-commit hooks)
2. **Broken frontend Gradle tasks** - `:frontend:vueRunDev` and `:frontend:vueLint` call `npm run serve` / `npm run lint`, which don't exist in `package.json`
3. **No advanced monitoring** - reactive issue detection
4. **Limited mobile responsiveness** - mobile user experience

---

## 🔄 Update Process

1. When a task is completed:
   - Update the checkbox `[ ]` → `[x]`
   - Add completion date and brief notes
   - Consider moving to "Recently Completed" section

2. When new tasks are identified:
   - Add to appropriate priority section
   - Include specific location and implementation details
   - Link to related issues or documentation

3. Regular review:
   - Review priorities monthly
   - Adjust based on project needs
   - Archive completed items periodically

---

*This document is the live backlog. For the phased roadmap and critical defects, see [functionality-audit.md](functionality-audit.md); for the doc index, see the root [README.md](../README.md).*
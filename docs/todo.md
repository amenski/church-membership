# MemberTracker Project - Todo List

*Last updated: February 17, 2026*  
*Generated from project review and implementation roadmap*

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
- [x] Added `validation-plan.md` with validation strategy
- [x] Updated `PROJECT_STATUS_AND_ROADMAP.md` with current status

---

## 🚨 Critical Priority (Must be done before production)

### Security
- [ ] **Role-Based Authorization Enhancement**
  - Add `@PreAuthorize` annotations to all controller methods
  - Implement fine-grained permission control (ADMIN, TREASURER, VIEWER)
  - Add permission checks in frontend routes
  - **Location**: All controller classes, frontend route guards

- [ ] **HTTPS Configuration**
  - Generate/obtain SSL certificates
  - Configure Spring Boot for HTTPS in production
  - Set up redirect from HTTP to HTTPS
  - **Location**: `application.properties`, deployment configuration

### Testing
- [ ] **Comprehensive Test Coverage**
  - Unit tests for all use cases (minimum 80% coverage)
  - Integration tests for all API endpoints
  - Frontend component tests (Vue.js)
  - **Location**: `src/test/`, frontend test files
  - **Tools**: JUnit 5, Spring Boot Test, Vue Test Utils

- [ ] **Test Infrastructure Setup**
  - Configure JaCoCo for code coverage reporting
  - Add test containers for database testing
  - Set up CI/CD pipeline with test execution
  - **Location**: `build.gradle`, GitHub Actions workflow

---

## 🔴 High Priority (Should be done soon)

### Core Features
- [ ] **Frontend Delivery Status Display**
  - Add delivery status column in communications table
  - Implement status summary cards
  - Add retry functionality for failed deliveries
  - **Location**: `frontend/src/views/CommunicationsView.vue`

- [ ] **Email Retry Logic Enhancement**
  - Implement exponential backoff retry strategy
  - Add retry configuration to `MailProperties`
  - Track retry attempts in `MessageDelivery`
  - **Location**: `EmailService`, `MailProperties`, retry use case

- [ ] **CSV Export Improvements**
  - Fix frontend blob handling (response.data vs response)
  - Add proper error handling for export failures
  - Implement streaming for large datasets
  - **Location**: `MemberController`, `PaymentController`, frontend views

### Code Quality
- [ ] **API Documentation**
  - Add OpenAPI/Swagger documentation
  - Document all endpoints with request/response examples
  - Generate interactive API documentation
  - **Location**: Swagger configuration, controller annotations

- [ ] **Error Handling Consistency**
  - Standardize error response format across all endpoints
  - Add localized error messages
  - Implement structured logging for errors
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

- [ ] **Member Search & Filtering**
  - Advanced search with multiple criteria
  - Save search filters as presets
  - Export search results
  - **Location**: `MemberController`, frontend search component

### Performance
- [ ] **Database Query Optimization**
  - Add indexes for common queries
  - Implement pagination for large result sets
  - Optimize N+1 query problems
  - **Location**: Entity definitions, repository methods

- [ ] **Frontend Performance**
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
2. **No advanced monitoring** - reactive issue detection
3. **Limited mobile responsiveness** - mobile user experience

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

*This document is maintained as part of the project documentation. For detailed implementation plans, see `IMPLEMENTATION_ROADMAP.md` and `PROJECT_STATUS_AND_ROADMAP.md`.*
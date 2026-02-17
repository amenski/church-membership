# Role-Based Authorization Checkpoints

## Progress Tracking

### Phase 1: Backend Role System (C1-C3)

- [x] **C1: Add TREASURER and VIEWER roles to UserRole enum**
  - Location: `src/main/java/io/github/membertracker/domain/enumeration/UserRole.java`
  - Add VIEWER, TREASURER roles with permission levels
  - Commit: 4d7c1d8 ✅

- [x] **C2: Enable method security in SecurityConfig**
  - Location: `src/main/java/io/github/membertracker/infrastructure/config/SecurityConfig.java`
  - Add @EnableMethodSecurity
  - Commit: 7287f42 ✅

- [x] **C3: Add @PreAuthorize to MemberController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/MemberController.java`
  - GET: VIEWER+, POST/PUT/DELETE: ADMIN only
  - Commit: 70c16f0 ✅

### Phase 2: Controllers (C4-C7)

- [ ] **C4: Add @PreAuthorize to PaymentController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/PaymentController.java`
  - GET: VIEWER+, POST: TREASURER+, DELETE: ADMIN only
  - Commit: ___

- [ ] **C5: Add @PreAuthorize to CommunicationController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`
  - GET: VIEWER+, POST: ADMIN only
  - Commit: ___

- [ ] **C6: Add @PreAuthorize to DashboardController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/DashboardController.java`
  - All endpoints: VIEWER+
  - Commit: ___

- [ ] **C7: Add @PreAuthorize to UserController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/UserController.java`
  - Authenticated users only
  - Commit: ___

### Phase 3: Frontend (C8-C9)

- [ ] **C8: Add role metadata to frontend routes**
  - Location: `frontend/src/router/index.js`
  - Add requiresRole meta to routes
  - Commit: ___

- [ ] **C9: Add role helpers to authStore**
  - Location: `frontend/src/stores/authStore.js`
  - Add isTreasurer, isViewer computed properties
  - Commit: ___

### Phase 4: Finalization (C10)

- [ ] **C10: Update todo.md progress**
  - Location: `docs/todo.md`
  - Mark Role-Based Authorization as completed
  - Commit: ___

---

## Permission Mapping

| Endpoint | Required Role |
|----------|---------------|
| GET /api/members | VIEWER+ |
| POST/PUT/DELETE /api/members | ADMIN only |
| GET /api/payments | VIEWER+ |
| POST /api/payments | TREASURER+ |
| DELETE /api/payments | ADMIN only |
| GET /api/communications | VIEWER+ |
| POST /api/communications | ADMIN only |
| GET /api/dashboard/* | VIEWER+ |
| GET /api/users/me | USER (own data) |
| PUT /api/users/me/* | USER (own data) |

## Role Hierarchy
- ADMIN: 4 (full access)
- TREASURER: 2 (payments)
- MANAGER: 3 (members, communications)
- VIEWER: 1 (read-only)
- USER: 1 (default)

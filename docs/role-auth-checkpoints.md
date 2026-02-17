# Role-Based Authorization Checkpoints

## Progress Tracking

### Phase 1: Backend Role System (C1-C3)

- [x] **C1: Add Admin, Staff, Volunteer, Member roles to UserRole enum**
  - Location: `src/main/java/io/github/membertracker/domain/enumeration/UserRole.java`
  - Add ADMIN, STAFF, VOLUNTEER, MEMBER roles (4-tier hierarchy)
  - Commit: abf4916 ✅

- [x] **C2: Enable method security in SecurityConfig**
  - Location: `src/main/java/io/github/membertracker/infrastructure/config/SecurityConfig.java`
  - Add @EnableMethodSecurity
  - Commit: 7287f42 ✅

- [x] **C3: Add @PreAuthorize to MemberController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/MemberController.java`
  - GET: VOLUNTEER+, POST/PUT: STAFF+, DELETE: ADMIN
  - Commit: abf4916 ✅

### Phase 2: Controllers (C4-C7)

- [ ] **C4: Add @PreAuthorize to PaymentController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/PaymentController.java`
  - GET: VOLUNTEER+, POST: STAFF+, DELETE: ADMIN only
  - Commit: ___

- [ ] **C5: Add @PreAuthorize to CommunicationController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`
  - GET: VOLUNTEER+, POST: ADMIN only
  - Commit: ___

- [ ] **C6: Add @PreAuthorize to DashboardController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/DashboardController.java`
  - All endpoints: VOLUNTEER+
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
| GET /api/members | VOLUNTEER+ |
| POST/PUT /api/members | STAFF+ |
| DELETE /api/members | ADMIN only |
| GET /api/payments | VOLUNTEER+ |
| POST /api/payments | STAFF+ |
| DELETE /api/payments | ADMIN only |
| GET /api/communications | VOLUNTEER+ |
| POST /api/communications | ADMIN only |
| GET /api/dashboard/* | VOLUNTEER+ |
| GET /api/users/me | MEMBER (own data) |
| PUT /api/users/me/* | MEMBER (own data) |

## Role Hierarchy
- ADMIN: 4 (full access)
- STAFF: 3 (manage members, communications)
- VOLUNTEER: 2 (limited operational access)
- MEMBER: 1 (basic authenticated access)

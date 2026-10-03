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

- [x] **C4: Add @PreAuthorize to PaymentController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/PaymentController.java`
  - GET: VOLUNTEER+, POST: STAFF+, DELETE: ADMIN
  - Commit: 3cf5d84 ✅

- [x] **C5: Add @PreAuthorize to CommunicationController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/CommunicationController.java`
  - GET: VOLUNTEER+, POST: STAFF+
  - Commit: 3cf5d84 ✅

- [x] **C6: Add @PreAuthorize to DashboardController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/DashboardController.java`
  - All endpoints: VOLUNTEER+
  - Commit: 3cf5d84 ✅

- [x] **C7: Add @PreAuthorize to UserController**
  - Location: `src/main/java/io/github/membertracker/infrastructure/UserController.java`
  - Authenticated users only
  - No change needed: /api/users/me* requires authentication via the security filter chain ✅

### Phase 3: Frontend (C8-C9)

- [x] **C8: Add role metadata to frontend routes**
  - Location: `frontend/src/router/index.js`
  - Add requiresRole meta to routes
  - Commit: feat(auth): role-aware frontend routes ✅

- [x] **C9: Add role helpers to authStore**
  - Location: `frontend/src/stores/authStore.js`
  - Add hasRole, isStaff, isVolunteer, homePath
  - Commit: feat(auth): role-aware frontend routes ✅

### Phase 4: Finalization (C10)

- [x] **C10: Update todo.md progress**
  - Location: `docs/todo.md`
  - Mark Role-Based Authorization as completed
  - Commit: feat(auth): role-aware frontend routes ✅

---

Current permissions and hierarchy: see [authentication.md](authentication.md#roles-and-permissions).

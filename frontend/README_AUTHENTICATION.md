# Frontend Authentication System Documentation

## Overview

The Member Tracker frontend implements a comprehensive, production-ready authentication system using Vue.js 3, Pinia for state management, and Axios for HTTP requests. The system uses **HttpOnly cookies** for token storage to prevent XSS attacks and follows security best practices.

## Architecture

### Components

1. **Auth Store** (`/src/stores/authStore.js`)
   - Centralized authentication state management
   - Handles login, logout, registration, and session monitoring
   - Provides computed properties for user state

2. **API Service** (`/src/services/api.js`)
   - Axios instance with interceptors for authentication
   - Automatic token refresh on 401 errors
   - CSRF token handling
   - Request/response logging and error handling

3. **Auth Interceptor** (`/src/utils/auth-interceptor.js`)
   - Global authentication error handling
   - Security event logging
   - Request validation for suspicious patterns

4. **Router Guards** (`/src/router/index.js`)
   - Route protection based on authentication status
   - Automatic redirection for unauthenticated users
   - Post-login redirect to intended destination
   - Session expiration handling

5. **Security Utilities** (`/src/utils/security.js`)
   - Input validation and sanitization
   - CSRF token management
   - Security event logging

## Features

### 1. Secure Authentication Flow

#### Login
- User submits email and password
- Credentials are validated client-side
- API call to `/v1/auth/login` with credentials
- Backend sets HttpOnly cookies (`sid` for access token, `sid_refresh` for refresh token)
- User data stored in Pinia store
- Session monitoring starts
- Redirect to intended destination or dashboard

#### Logout
- API call to `/v1/auth/logout`
- Backend clears cookies
- Local state cleared from Pinia store
- Session monitoring stopped
- Redirect to login page

#### Registration
- User submits registration form with validation
- API call to `/v1/auth/register`
- Success redirects to login page
- User can then log in with new credentials

### 2. Session Management

#### Activity Tracking
- User activity is tracked on every interaction
- Session timeout set to 1 hour by default (configurable)
- Automatic logout on session expiration

#### Token Refresh
- Automatic token refresh on 401 errors
- Uses refresh token from `sid_refresh` cookie
- Retries original request after successful refresh
- Redirects to login if refresh fails

#### Session Monitoring
- Background check every 30 seconds for session expiration
- User activity events (mousedown, mousemove, keypress, scroll, touchstart)
- Activity timestamp updated on each request

### 3. Route Protection

All routes except `/login` and `/register` require authentication:

```javascript
{
  path: '/dashboard',
  name: 'dashboard',
  component: Dashboard,
  meta: { requiresAuth: true }  // Protected route
}
```

Navigation guard checks:
1. Authentication status
2. Session expiration
3. Role-based access (if configured)
4. Redirects unauthenticated users to login
5. Preserves intended destination for post-login redirect

### 4. Security Features

#### CSRF Protection
- Automatic CSRF token extraction from cookies
- Token attached to all non-GET requests via `X-XSRF-TOKEN` header
- Backend validates CSRF token on state-changing operations

#### Input Validation
- Client-side validation for all form inputs
- Email format validation
- Password strength requirements
- Maximum input length restrictions
- XSS prevention through input sanitization

#### Security Event Logging
- Failed login attempts logged
- Session expirations logged
- Suspicious request patterns detected and logged
- Security reports available via `security.getReport()`

#### HttpOnly Cookies
- Tokens stored in HttpOnly cookies (not accessible to JavaScript)
- Prevents XSS attacks
- Secure flag set in production
- SameSite policy to prevent CSRF

### 5. Error Handling

#### API Errors
- 401 Unauthorized: Automatic token refresh, then redirect to login
- 403 Forbidden: Access denied notification, no retry
- 429 Too Many Requests: Rate limiting notification
- 500 Server Error: Generic error with retry
- Network errors: Automatic retry with exponential backoff

#### User-Friendly Messages
- Validation errors shown inline
- Authentication errors displayed in alert
- Toast notifications for success/error states
- Session expiration warnings

## Configuration

### Environment Variables

#### Development (`.env.development`)
```bash
VUE_APP_API_BASE_URL=/api
VUE_APP_API_TIMEOUT=30000
VUE_APP_API_RETRY_ATTEMPTS=3
VUE_APP_CSP_ENABLED=false
VUE_APP_SESSION_TIMEOUT=3600000  # 1 hour in milliseconds
```

#### Production (`.env.production`)
```bash
VUE_APP_API_BASE_URL=/api
VUE_APP_API_TIMEOUT=30000
VUE_APP_API_RETRY_ATTEMPTS=3
VUE_APP_CSP_ENABLED=true
VUE_APP_SESSION_TIMEOUT=3600000
```

### Session Timeout Configuration

You can customize the session timeout in the auth store:

```javascript
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
authStore.setSessionTimeout(30 * 60 * 1000) // 30 minutes
```

## Usage

### Accessing Authentication State

```javascript
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()

// Check authentication status
if (authStore.isLoggedIn) {
  console.log('User is authenticated')
}

// Get current user
const user = authStore.currentUser
console.log(user.email, user.firstName, user.lastName)

// Check user role
if (authStore.isAdmin) {
  console.log('User is an admin')
}
```

### Login Programmatically

```javascript
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

try {
  await authStore.login({
    email: 'user@example.com',
    password: 'password123'
  })
  router.push('/dashboard')
} catch (error) {
  console.error('Login failed:', error)
}
```

### Logout Programmatically

```javascript
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

try {
  await authStore.logout()
  router.push('/login')
} catch (error) {
  console.error('Logout failed:', error)
}
```

### Protecting Routes

Add `meta: { requiresAuth: true }` to any route that requires authentication:

```javascript
{
  path: '/admin',
  name: 'admin',
  component: AdminView,
  meta: {
    requiresAuth: true,
    requiresRole: 'ADMIN'  // Optional: role-based access
  }
}
```

### Making Authenticated API Requests

The API service automatically includes authentication cookies:

```javascript
import apiService from '@/services/api'

// Get authenticated user's data
const userData = await apiService.getCurrentUser()

// Make authenticated request
const members = await apiService.getMembers()
```

## Security Best Practices

### 1. Token Storage
- Tokens stored in HttpOnly cookies (recommended)
- Never store tokens in localStorage or sessionStorage
- Backend sets `Secure` flag in production
- Backend sets `SameSite=Lax` or `SameSite=Strict`

### 2. CSRF Protection
- CSRF token cookie set by backend
- Frontend automatically includes token in headers
- Backend validates token on state-changing requests

### 3. Session Management
- Session timeout enforced client-side and server-side
- Automatic logout on expiration
- Activity tracking to extend session

### 4. Input Validation
- Always validate and sanitize user input
- Use the security utilities for consistent validation
- Server-side validation is the ultimate authority

### 5. HTTPS
- Always use HTTPS in production
- Cookies with `Secure` flag only sent over HTTPS
- Man-in-the-middle attack prevention

## Troubleshooting

### Login Fails with 401
- Check backend is running
- Verify API endpoint `/v1/auth/login` is accessible
- Check credentials are correct
- Verify CORS is configured on backend

### Session Expires Immediately
- Check session timeout configuration
- Verify backend cookie settings
- Check browser cookie settings
- Ensure clocks are synchronized (client/server)

### CSRF Token Errors
- Verify backend is sending CSRF token cookie
- Check cookie name matches (default: `XSRF-TOKEN`)
- Ensure `withCredentials: true` in Axios config
- Verify CORS allows credentials

### Token Refresh Fails
- Check refresh endpoint `/v1/auth/refresh` is accessible
- Verify refresh token cookie is being sent
- Check refresh token expiration
- Ensure refresh token rotation is working

## Testing

### Manual Testing

1. **Login Flow**
   ```bash
   # Login
   curl -i -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"user@example.com","password":"password"}'

   # Verify cookies set
   cat cookies.txt

   # Make authenticated request
   curl -b cookies.txt http://localhost:8080/api/v1/users/me
   ```

2. **Token Refresh**
   ```bash
   # Refresh tokens
   curl -i -b cookies.txt -c cookies.txt \
     -X POST http://localhost:8080/api/v1/auth/refresh
   ```

3. **Logout**
   ```bash
   # Logout
   curl -i -b cookies.txt -X POST http://localhost:8080/api/v1/auth/logout

   # Verify authenticated request fails
   curl -b cookies.txt http://localhost:8080/api/v1/users/me
   # Should return 401
   ```

### Unit Testing

Test the auth store:

```javascript
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('logs in user successfully', async () => {
    const authStore = useAuthStore()
    await authStore.login({
      email: 'test@example.com',
      password: 'password'
    })
    expect(authStore.isLoggedIn).toBe(true)
  })

  it('logs out user', async () => {
    const authStore = useAuthStore()
    await authStore.login({ email: 'test@example.com', password: 'password' })
    await authStore.logout()
    expect(authStore.isLoggedIn).toBe(false)
  })
})
```

## Migration Guide

### From Bearer Tokens to Cookies

If migrating from Bearer token authentication:

1. Backend supports both methods during transition
2. Frontend switches to cookie-based auth
3. Remove localStorage/sessionStorage token storage
4. Update API requests to use `withCredentials: true`
5. Test thoroughly
6. Deprecate Bearer token support

### Enabling CSRF Protection

1. Backend: Enable CSRF token cookie
   ```java
   http.csrf(csrf -> csrf
     .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
   ```

2. Frontend: Verify CSRF token handling is enabled (already implemented)

3. Test all state-changing operations

## References

- [Backend Cookie Auth Documentation](../doc/backend-cookie-auth.md)
- [Vue Router Navigation Guards](https://router.vuejs.org/guide/advanced/navigation-guards.html)
- [Pinia Documentation](https://pinia.vuejs.org/)
- [Axios Interceptors](https://axios-http.com/docs/interceptors)
- [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

# Authentication System Documentation

## Overview

The Member Tracker application implements a comprehensive, production-ready authentication system with the following features:

- **Secure Login/Logout**: Cookie-based authentication with CSRF protection
- **User Registration**: Full user registration flow with validation
- **Route Protection**: All routes require authentication except login/register
- **Session Management**: Automatic session tracking with inactivity timeout
- **Token Refresh**: Automatic token refresh on 401 responses
- **Security Monitoring**: Event logging and security best practices
- **Auto-redirect**: Unauthenticated users automatically redirected to login

## Architecture

### Components

1. **authStore.js** - Pinia store managing authentication state
2. **api.js** - Axios instance with authentication interceptors
3. **router/index.js** - Route guards for authentication
4. **LoginView.vue** - Login page component
5. **RegisterView.vue** - Registration page component

## Key Features

### 1. Authentication Store (`authStore.js`)

The authentication store is the central piece of the authentication system, managing:

- **User State**: Current user information and authentication status
- **Session Management**: Activity tracking and session timeout
- **Login/Logout**: Authentication operations with API integration
- **Error Handling**: Comprehensive error messages for different scenarios

#### Key Functions

```javascript
// Login
await authStore.login({ email, password })

// Logout
await authStore.logout()

// Check authentication status
await authStore.checkAuth()

// Force logout (e.g., on security event)
authStore.forceLogout()

// Update activity (session tracking)
authStore.updateActivity()
```

#### State Properties

- `user`: Current user object
- `isAuthenticated`: Boolean indicating auth status
- `isLoading`: Loading state for async operations
- `error`: Current error message
- `authChecked`: Whether initial auth check completed
- `lastActivity`: Timestamp of last user activity
- `sessionTimeout`: Session timeout duration (default: 1 hour)

#### Computed Properties

- `currentUser`: Current user object
- `isLoggedIn`: Authentication status
- `userRole`: User's role (ADMIN, USER, etc.)
- `isAdmin`: Whether user is admin
- `sessionExpired`: Whether session has expired
- `timeUntilExpiry`: Time remaining until session expires

### 2. Route Protection (`router/index.js`)

All routes are protected by a global navigation guard that:

1. **Checks Authentication**: Verifies user is authenticated before allowing access
2. **Session Validation**: Checks if session has expired
3. **Auto-redirect**: Redirects unauthenticated users to login
4. **Preserves Destination**: Stores intended route for post-login redirect
5. **Guest Routes**: Prevents authenticated users from accessing login/register
6. **Role-based Access**: (Optional) Restricts routes by user role

#### Route Configuration

```javascript
{
  path: '/dashboard',
  name: 'dashboard',
  component: Dashboard,
  meta: {
    requiresAuth: true,
    requiresRole: ['ADMIN', 'USER'] // Optional
  }
}
```

#### Navigation Guard Flow

```
User navigates to route
    ↓
Check if auth status has been verified
    ↓
If not verified → checkAuth()
    ↓
Check session expiration
    ↓
If expired → logout → redirect to /login?session=expired
    ↓
Check route requirements
    ↓
requiresAuth && !authenticated → redirect to /login?redirect=/intended-route
    ↓
requiresGuest && authenticated → redirect to /dashboard
    ↓
requiresRole && !hasRole → redirect to /?error=access_denied
    ↓
Allow navigation
```

### 3. API Interceptors (`api.js`)

The API service includes request and response interceptors that:

#### Request Interceptor
- Adds CSRF token to non-GET requests
- Updates activity timestamp
- Adds cache-busting timestamp to GET requests
- Logs requests in development

#### Response Interceptor
- **401 Handling**: Attempts token refresh, retries request, or redirects to login
- **403 Handling**: Logs security events for access denial
- **Network Error Retry**: Retries failed requests with exponential backoff
- **Error Logging**: Comprehensive error logging

#### Token Refresh Flow

```
API Request → 401 Response
    ↓
Not already retrying?
    ↓
Attempt token refresh via /v1/auth/refresh
    ↓
Success? → Retry original request
    ↓
Failure? → Clear auth state → Redirect to /login?session=expired
```

### 4. Login Component (`LoginView.vue`)

Features:
- Email and password validation
- Real-time field validation
- Error display with specific messages
- Loading states
- Auto-redirect after successful login
- Session expired notification
- Responsive design

### 5. Registration Component (`RegisterView.vue`)

Features:
- Multi-field validation (firstName, lastName, email, password)
- Password confirmation matching
- Terms of service agreement
- Real-time validation feedback
- Success notification and redirect to login
- Input sanitization

### 6. App Component (`App.vue`)

Features:
- Conditional navbar display (only when authenticated)
- User menu with logout
- Toast notification system
- Logout handler
- Store initialization

## API Endpoints

### Authentication Endpoints

```
POST /v1/auth/login
  Body: { email, password }
  Response: { user: {...} }
  Sets authentication cookies

POST /v1/auth/logout
  Response: Success message
  Clears authentication cookies

POST /v1/auth/refresh
  Response: { user: {...} }
  Refreshes authentication token

POST /v1/auth/register
  Body: { email, password, firstName, lastName }
  Response: Success message

GET /users/me
  Response: Current user object
  Requires authentication
```

## Security Features

### 1. Cookie-based Authentication
- `withCredentials: true` on all API requests
- HttpOnly cookies for token storage (server-side)
- CSRF protection via XSRF-TOKEN cookie

### 2. Session Management
- Activity tracking on every user interaction
- Configurable session timeout (default: 1 hour)
- Automatic logout on session expiration
- Session monitoring every 30 seconds

### 3. Input Validation
- Email format validation
- Password strength requirements (min 6 characters)
- Client-side form validation

### 4. Error Handling
- Specific error messages for different scenarios
- No sensitive information leakage
- Rate limiting awareness (429 status)
- Network error recovery

## User Flow

### First Visit (Unauthenticated)
```
User visits any route
    ↓
Router guard checks authentication
    ↓
Not authenticated
    ↓
Redirect to /login?redirect=/intended-route
    ↓
User enters credentials
    ↓
Login → authStore.login()
    ↓
Success → Redirect to intended route or /dashboard
```

### Authenticated Session
```
User authenticated
    ↓
All routes accessible
    ↓
Activity tracked on interactions
    ↓
Session timeout monitored
    ↓
If inactive > 1 hour → Auto logout → Redirect to /login?session=expired
```

### Token Expiration
```
User makes API request
    ↓
Server returns 401 (token expired)
    ↓
Interceptor catches 401
    ↓
Attempt token refresh
    ↓
Success → Retry original request
    ↓
Failure → Clear auth → Redirect to /login?session=expired
```

### Logout
```
User clicks logout
    ↓
authStore.logout()
    ↓
Call /v1/auth/logout API
    ↓
Clear local auth state
    ↓
Stop session monitoring
    ↓
Redirect to /login
    ↓
Show success notification
```

## Configuration

### Environment Variables

```bash
# API Configuration
VUE_APP_API_BASE_URL=/api
VUE_APP_API_TIMEOUT=30000
VUE_APP_API_RETRY_ATTEMPTS=3

# Application
VUE_APP_APP_TITLE=Member Tracker
VUE_APP_APP_VERSION=1.0.0

# Security
VUE_APP_CSP_ENABLED=false
```

### Session Timeout

```javascript
// In authStore.js - default 1 hour
const sessionTimeout = ref(60 * 60 * 1000)

// Can be changed at runtime
authStore.setSessionTimeout(30 * 60 * 1000) // 30 minutes
```

### Retry Configuration

```javascript
// In api.js
const API_CONFIG = {
  retryAttempts: 3,
  retryDelay: 1000 // 1 second
}
```

## Testing the Authentication Flow

### Manual Testing Checklist

1. **Login Flow**
   - [ ] Visit app → Redirected to login
   - [ ] Invalid credentials → Error message
   - [ ] Valid credentials → Login success → Redirect to dashboard
   - [ ] Intended route preserved after login

2. **Registration Flow**
   - [ ] All fields required
   - [ ] Email validation working
   - [ ] Password confirmation matching
   - [ ] Successful registration → Redirect to login

3. **Protected Routes**
   - [ ] All routes require authentication
   - [ ] Unauthenticated access redirected to login
   - [ ] Login page accessible when not authenticated
   - [ ] Register page accessible when not authenticated

4. **Session Management**
   - [ ] Activity tracking working
   - [ ] Session timeout after inactivity
   - [ ] Auto logout on session expiration

5. **Logout**
   - [ ] Logout button works
   - [ ] Auth state cleared
   - [ ] Redirected to login
   - [ ] Cannot access protected routes after logout

6. **Token Refresh**
   - [ ] 401 triggers token refresh attempt
   - [ ] Successful refresh retries original request
   - [ ] Failed refresh logs out user

7. **Security**
   - [ ] CSRF token included in requests
   - [ ] Suspicious patterns blocked
   - [ ] Security events logged

## Troubleshooting

### Common Issues

#### 1. Infinite redirect loop
**Cause**: Router guard not detecting auth state correctly
**Solution**: Ensure `authChecked` flag is set after initial auth check

#### 2. Session expires too quickly
**Cause**: Activity tracking not working
**Solution**: Verify event listeners are attached in authStore initialization

#### 3. 401 errors not triggering refresh
**Cause**: Request already marked as retry
**Solution**: Check `_retry` flag logic in response interceptor

#### 4. CSRF token missing
**Cause**: Cookie not being read correctly
**Solution**: Verify `withCredentials: true` and check cookie name matches

#### 5. User data not persisting across page refresh
**Cause**: `checkAuth()` not being called on app initialization
**Solution**: Ensure `authStore.initialize()` is called in App.vue

## Best Practices

1. **Always use authStore methods** - Don't directly manipulate auth state
2. **Check authentication status** - Use `authStore.isLoggedIn` before making auth-dependent calls
3. **Handle errors gracefully** - Show user-friendly error messages
4. **Log security events** - Use `security.logEvent()` for important events
5. **Validate all input** - Use `security.validate()` for user input
6. **Keep sessions secure** - Don't extend timeout excessively
7. **Monitor activity** - Ensure user activity updates on meaningful interactions

## Future Enhancements

- [ ] Two-factor authentication (2FA)
- [ ] OAuth/Social login integration
- [ ] Remember me functionality
- [ ] Password reset flow
- [ ] Email verification
- [ ] Account lockout after failed attempts
- [ ] Advanced role-based permissions
- [ ] Audit logging dashboard
- [ ] Session management (view/revoke sessions)
- [ ] Security settings page

## Related Documentation

- [Backend Cookie Authentication](./backend-cookie-auth.md)
- [Security Best Practices](./security.js)
- [Domain Model Strategy](./DOMAIN_MODEL_STRATEGY.md)
- [Architectural Improvement Plan](./ARCHITECTURAL_IMPROVEMENT_PLAN.md)

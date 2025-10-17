# Authentication Quick Reference Guide

## Quick Start

### 1. Login a User

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

### 2. Logout a User

```javascript
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

await authStore.logout()
router.push('/login')
```

### 3. Check Authentication Status

```javascript
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()

// Simple check
if (authStore.isLoggedIn) {
  console.log('User is logged in')
}

// Get current user
const user = authStore.currentUser
console.log(user.email, user.firstName, user.lastName)

// Check role
if (authStore.isAdmin) {
  console.log('User is an admin')
}
```

### 4. Make Authenticated API Requests

```javascript
import apiService from '@/services/api'

// The API service automatically includes authentication cookies
const members = await apiService.getMembers()
const payments = await apiService.getPayments()
```

### 5. Protect a Route

```javascript
// In router/index.js
{
  path: '/admin',
  name: 'admin',
  component: AdminView,
  meta: {
    requiresAuth: true,        // Require authentication
    requiresRole: 'ADMIN'      // Optional: require specific role
  }
}
```

## Common Tasks

### Access Current User in Component

```vue
<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const currentUser = computed(() => authStore.currentUser)
</script>

<template>
  <div v-if="currentUser">
    Welcome, {{ currentUser.firstName }} {{ currentUser.lastName }}!
  </div>
</template>
```

### Handle Session Expiration

```javascript
// The system automatically handles session expiration
// You can customize the timeout:

import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()

// Set timeout to 30 minutes
authStore.setSessionTimeout(30 * 60 * 1000)
```

### Show/Hide Content Based on Authentication

```vue
<template>
  <!-- Show only to authenticated users -->
  <div v-if="isLoggedIn">
    <h1>Welcome back!</h1>
  </div>

  <!-- Show only to guests -->
  <div v-else>
    <router-link to="/login">Please log in</router-link>
  </div>

  <!-- Show only to admins -->
  <div v-if="isAdmin">
    <button>Admin Panel</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const isLoggedIn = computed(() => authStore.isLoggedIn)
const isAdmin = computed(() => authStore.isAdmin)
</script>
```

### Add Loading State to Login Form

```vue
<template>
  <form @submit.prevent="handleLogin">
    <input v-model="email" type="email" :disabled="isAuthLoading" />
    <input v-model="password" type="password" :disabled="isAuthLoading" />
    <button type="submit" :disabled="isAuthLoading">
      <span v-if="isAuthLoading">Signing in...</span>
      <span v-else>Sign In</span>
    </button>
  </form>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const isAuthLoading = computed(() => authStore.isAuthLoading)

const email = ref('')
const password = ref('')

const handleLogin = async () => {
  await authStore.login({ email: email.value, password: password.value })
}
</script>
```

### Display Authentication Errors

```vue
<template>
  <div v-if="authError" class="alert alert-danger">
    {{ authError }}
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const authError = computed(() => authStore.authError)
</script>
```

## API Endpoints

### Authentication Endpoints

| Method | Endpoint | Description | Cookie Response |
|--------|----------|-------------|-----------------|
| POST | `/v1/auth/login` | Login with credentials | Sets `sid` and `sid_refresh` cookies |
| POST | `/v1/auth/logout` | Logout current user | Clears cookies |
| POST | `/v1/auth/refresh` | Refresh access token | Updates `sid` cookie |
| POST | `/v1/auth/register` | Register new user | No cookies (must login after) |
| GET | `/v1/users/me` | Get current user info | Requires authentication |

### Example API Calls

```javascript
import apiService from '@/services/api'

// Login
const response = await apiService.login({
  email: 'user@example.com',
  password: 'password123'
})

// Get current user
const user = await apiService.getCurrentUser()

// Logout
await apiService.logout()

// Refresh token (usually automatic)
await apiService.refreshToken()
```

## Environment Variables

### Required Variables

```bash
# API Configuration
VUE_APP_API_BASE_URL=/api
VUE_APP_API_TIMEOUT=30000
VUE_APP_API_RETRY_ATTEMPTS=3

# Security
VUE_APP_CSP_ENABLED=false  # true in production
VUE_APP_SESSION_TIMEOUT=3600000  # 1 hour in ms
```

### Access in Code

```javascript
// API base URL
const apiUrl = process.env.VUE_APP_API_BASE_URL

// Session timeout
const timeout = parseInt(process.env.VUE_APP_SESSION_TIMEOUT || '3600000')
```

## Debugging

### Enable Debug Logging

In development, authentication logs are automatically enabled. Check the browser console for:
- Login/logout events
- API requests/responses
- Session monitoring
- Security events

### Check Authentication State

```javascript
// Open browser console and run:
const authStore = window.useAuthStore()
console.log('Is Logged In:', authStore.isLoggedIn)
console.log('Current User:', authStore.currentUser)
console.log('Session Timeout:', authStore.timeUntilExpiry)
console.log('Has Auth Checked:', authStore.hasAuthChecked)
```

### View Security Events

```javascript
import { security } from '@/utils/security'

// Get security report
const report = security.getReport()
console.log('Security Events:', report.events)
```

### Test CSRF Token

```javascript
// Check if CSRF token is present in cookies
const csrfToken = document.cookie
  .split('; ')
  .find(row => row.startsWith('XSRF-TOKEN='))
  ?.split('=')[1]

console.log('CSRF Token:', csrfToken)
```

## Common Issues and Solutions

### Issue: Login Returns 401

**Solution**: Check backend is running and CORS is configured correctly.

```javascript
// Verify API endpoint
console.log('API Base URL:', process.env.VUE_APP_API_BASE_URL)

// Check network tab in browser dev tools
// Look for CORS errors
```

### Issue: Session Expires Immediately

**Solution**: Check session timeout configuration.

```javascript
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
console.log('Session Timeout:', authStore.sessionTimeout)
console.log('Time Until Expiry:', authStore.timeUntilExpiry)
```

### Issue: CSRF Token Not Sent

**Solution**: Verify cookies are being sent with requests.

```javascript
// Check axios config
import { axiosInstance } from '@/services/api'

console.log('With Credentials:', axiosInstance.defaults.withCredentials)
// Should be true
```

### Issue: Redirect Loop After Login

**Solution**: Check navigation guard logic.

```javascript
// In router/index.js, ensure:
// 1. Login/register routes have meta: { requiresGuest: true }
// 2. Navigation guard checks for this before redirecting
```

## Security Checklist

- ✅ Use HTTPS in production
- ✅ Set CSP enabled in production
- ✅ Never log passwords or tokens
- ✅ Always validate user input
- ✅ Use the security utilities for validation
- ✅ Keep session timeout reasonable (1 hour recommended)
- ✅ Test logout clears all state
- ✅ Verify CSRF tokens on state-changing requests
- ✅ Monitor security events in production

## Performance Tips

1. **Minimize Auth Checks**: Auth state is reactive, use computed properties
2. **Cache User Data**: User info is stored in Pinia, no need to fetch repeatedly
3. **Debounce Activity Updates**: Activity is already throttled by event listeners
4. **Use Route Meta**: Define access control in routes, not in components

## Testing

### Manual Testing Commands

```bash
# Login
curl -i -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"password"}'

# Check authentication
curl -b cookies.txt http://localhost:8080/api/v1/users/me

# Logout
curl -i -b cookies.txt -X POST http://localhost:8080/api/v1/auth/logout
```

### Unit Test Example

```javascript
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with unauthenticated state', () => {
    const authStore = useAuthStore()
    expect(authStore.isLoggedIn).toBe(false)
    expect(authStore.currentUser).toBeNull()
  })
})
```

## Resources

- [Full Authentication Documentation](./README_AUTHENTICATION.md)
- [Implementation Summary](./AUTHENTICATION_IMPLEMENTATION_SUMMARY.md)
- [Backend Cookie Auth Guide](../doc/backend-cookie-auth.md)
- [Vue Router Docs](https://router.vuejs.org/)
- [Pinia Docs](https://pinia.vuejs.org/)
- [Axios Docs](https://axios-http.com/)

# Authentication Quick Start Guide

## For Developers

### Getting Started

The authentication system is fully implemented and ready to use. All routes require authentication by default.

### Key Files

```
frontend/src/
├── stores/
│   └── authStore.js          # Authentication state management
├── services/
│   └── api.js                # API client with auth interceptors
├── router/
│   └── index.js              # Route guards
└── views/
    ├── LoginView.vue         # Login page
    └── RegisterView.vue      # Registration page
```

### Using Authentication in Components

#### Check if user is authenticated

```vue
<template>
  <div v-if="isLoggedIn">
    Welcome {{ currentUser.firstName }}!
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const isLoggedIn = computed(() => authStore.isLoggedIn)
const currentUser = computed(() => authStore.currentUser)
</script>
```

#### Logout programmatically

```vue
<script setup>
import { useAuthStore } from '@/stores/authStore'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>
```

#### Check user role

```vue
<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)
const userRole = computed(() => authStore.userRole)
</script>
```

### Protecting Routes

Add `requiresAuth` meta to routes:

```javascript
{
  path: '/admin',
  name: 'admin',
  component: AdminView,
  meta: {
    requiresAuth: true,
    requiresRole: ['ADMIN']  // Optional: restrict by role
  }
}
```

### Making Authenticated API Calls

All API calls automatically include authentication:

```javascript
import api from '@/services/api'

// Authentication is handled automatically
const members = await api.getMembers()
const user = await api.getCurrentUser()
```

### Handling Authentication Errors

```javascript
import { useAuthStore } from '@/stores/authStore'

const authStore = useAuthStore()

try {
  await authStore.login({ email, password })
} catch (error) {
  // Error message available in authStore.authError
  console.error(authStore.authError)
}
```

### Security Best Practices

#### Validate user input

Form validation is handled in the LoginView and RegisterView components. The authStore also validates email format before making API calls.

## For Users

### How to Login

1. Navigate to the application
2. If not logged in, you'll be redirected to the login page
3. Enter your email and password
4. Click "Sign In"
5. You'll be redirected to your intended destination or the dashboard

### How to Register

1. Click "Sign up here" on the login page
2. Fill in all required fields:
   - First Name
   - Last Name
   - Email Address
   - Password (min 6 characters)
   - Confirm Password
3. Agree to Terms of Service
4. Click "Create Account"
5. You'll be redirected to login with your new credentials

### Session Management

- Your session will remain active as long as you're using the application
- After 1 hour of inactivity, you'll be automatically logged out
- You'll see a "Session Expired" message and can log in again

### Security

- All communication is encrypted
- Passwords are never stored in plain text
- Your session is protected by security tokens
- Suspicious activity is monitored and logged

## Testing

### Manual Test

1. **Start the application**
   ```bash
   cd frontend
   npm run serve
   ```

2. **Visit the app**
   - Go to http://localhost:8080
   - Should redirect to /login

3. **Try to access protected route**
   - Go to http://localhost:8080/members
   - Should redirect to /login?redirect=/members

4. **Register a new account**
   - Fill in registration form
   - Submit
   - Should redirect to login

5. **Login**
   - Enter your credentials
   - Submit
   - Should redirect to dashboard (or previous route)

6. **Test protected features**
   - Navigate to different pages
   - All should work without redirect

7. **Logout**
   - Click user menu → Sign Out
   - Should redirect to login
   - Try accessing protected route → Should redirect to login

### Common Scenarios

#### Scenario 1: First-time User
```
Visit app → Redirected to login → Click "Sign up here" →
Fill registration → Submit → Redirected to login →
Enter credentials → Dashboard
```

#### Scenario 2: Returning User
```
Visit app → Redirected to login → Enter credentials →
Dashboard (or last visited page)
```

#### Scenario 3: Session Timeout
```
Logged in → Inactive for 1 hour → Make request →
Logged out → Redirected to login with "Session Expired" message
```

#### Scenario 4: Token Expiry
```
Logged in → Token expires → Make API call →
Automatic token refresh → Continue working
(If refresh fails → Logout → Login page)
```

## Troubleshooting

### I keep getting logged out
- Check session timeout settings
- Ensure you're interacting with the app (mouse/keyboard)
- Check browser console for errors

### Login form shows error
- Check credentials are correct
- Ensure backend is running
- Check network tab for API errors
- Verify CORS settings

### Redirected to login unexpectedly
- Session may have expired
- Token may have been invalidated
- Backend may have restarted
- Check browser console for errors

### Registration not working
- Check all required fields are filled
- Ensure passwords match
- Email must be valid format
- Check network tab for API response

## API Integration

### Backend Requirements

The frontend expects these API endpoints:

```
POST /v1/auth/login
  Request: { email: string, password: string }
  Response: { user: { id, email, firstName, lastName, role } }
  Sets: Authentication cookies (HttpOnly, Secure)

POST /v1/auth/logout
  Response: { message: string }
  Clears: Authentication cookies

POST /v1/auth/refresh
  Response: { user: { ... } }
  Refreshes: Authentication token

POST /v1/auth/register
  Request: { email, password, firstName, lastName }
  Response: { message: string }

GET /users/me
  Response: { id, email, firstName, lastName, role, ... }
  Requires: Authentication
```

### Cookie Configuration

Backend should set cookies with:
- `HttpOnly: true` - Prevent JavaScript access
- `Secure: true` - HTTPS only (production)
- `SameSite: Strict/Lax` - CSRF protection
- Cookie name: Should be consistent with XSRF-TOKEN for CSRF

## Configuration

### Adjust Session Timeout

In `authStore.js`:

```javascript
const sessionTimeout = ref(30 * 60 * 1000) // 30 minutes
```

### Change API Retry Settings

In `api.js`:

```javascript
const API_CONFIG = {
  retryAttempts: 5,
  retryDelay: 2000
}
```


## Support

For issues or questions:
- Check the main documentation: `doc/AUTHENTICATION_SYSTEM.md`
- Review backend docs: `doc/backend-cookie-auth.md`
- Check browser console for errors
- Review network tab for API issues
- Contact development team

## Next Steps

Once authentication is working:

1. Test all features thoroughly
2. Add role-based access control if needed
3. Implement password reset flow
4. Add email verification
5. Set up monitoring and logging
6. Review security audit checklist
7. Configure production environment
8. Set up HTTPS certificates
9. Review and harden CORS settings
10. Implement rate limiting on backend

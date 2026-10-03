<template>
  <main class="auth">
    <router-link to="/" class="auth__brand" aria-label="Felege Selam home">
      <span class="auth__geez">ፈለገ ሰላም</span>
      <span class="auth__latin">Felege Selam</span>
    </router-link>

    <div class="auth__card">
      <WovenBand :height="8" />
      <div class="auth__body">
        <h1 class="auth__title">Sign in</h1>

        <form class="auth__form" @submit.prevent="handleLogin">
          <!-- Error Alert -->
          <div v-if="authError" class="alert alert-danger" role="alert">
            {{ authError }}
          </div>

          <!-- Email Field -->
          <div class="mb-3">
            <label for="email" class="form-label">Email</label>
            <input
              id="email"
              v-model="form.email"
              type="email"
              class="form-control"
              :class="{ 'is-invalid': errors.email }"
              autocomplete="username"
              :disabled="isAuthLoading"
              @blur="validateField('email')"
            />
            <div v-if="errors.email" class="invalid-feedback">
              {{ errors.email }}
            </div>
          </div>

          <!-- Password Field -->
          <div class="mb-4">
            <label for="password" class="form-label">Password</label>
            <input
              id="password"
              v-model="form.password"
              type="password"
              class="form-control"
              :class="{ 'is-invalid': errors.password }"
              autocomplete="current-password"
              :disabled="isAuthLoading"
              @blur="validateField('password')"
            />
            <div v-if="errors.password" class="invalid-feedback">
              {{ errors.password }}
            </div>
          </div>

          <!-- Submit Button -->
          <button
            type="submit"
            class="btn btn-primary w-100 justify-content-center"
            :disabled="isAuthLoading || !isFormValid"
          >
            <span v-if="isAuthLoading" class="spinner-border spinner-border-sm me-2" role="status"></span>
            {{ isAuthLoading ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
      </div>
    </div>

    <!-- Registration is disabled: accounts are created by the church office -->
    <p class="auth__note">Accounts are set up by the church office.</p>
  </main>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import WovenBand from '@/components/WovenBand.vue'

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

// Email validation helper
const isValidEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

// Input sanitization helper
const sanitizeInput = (input) => {
  if (!input) return input
  return input.toString().trim()
}

// Form state
const form = reactive({
  email: '',
  password: ''
})

// Validation errors
const errors = reactive({
  email: '',
  password: ''
})

// Computed properties
const isAuthLoading = computed(() => authStore.isAuthLoading)
const authError = computed(() => authStore.authError)
const isFormValid = computed(() => {
  return form.email.trim() &&
         form.password.trim() &&
         !errors.email &&
         !errors.password
})

// Validation methods
const validateField = (field) => {
  switch (field) {
    case 'email':
      if (!form.email.trim()) {
        errors.email = 'Email is required'
      } else if (!isValidEmail(form.email)) {
        errors.email = 'Please enter a valid email address'
      } else {
        errors.email = ''
      }
      break

    case 'password':
      if (!form.password.trim()) {
        errors.password = 'Password is required'
      } else {
        errors.password = ''
      }
      break
  }
}

const validateForm = () => {
  validateField('email')
  validateField('password')
  return !errors.email && !errors.password
}

// Login handler
const handleLogin = async () => {
  // Clear previous errors
  authStore.clearError()

  // Validate form
  if (!validateForm()) {
    appStore.addNotification({
      type: 'warning',
      title: 'Validation Error',
      message: 'Please fix the errors in the form',
      isToast: true
    })
    return
  }

  try {
    // Sanitize input
    const credentials = {
      email: sanitizeInput(form.email),
      password: form.password // Don't sanitize password
    }

    // Call login action
    await authStore.login(credentials)

    // Show success notification
    appStore.addNotification({
      type: 'success',
      title: 'Welcome back!',
      message: 'You have successfully signed in',
      isToast: true
    })

    // Get redirect path from query parameter or default to dashboard
    const route = router.currentRoute.value
    const redirectPath = route.query.redirect || '/'

    // Check for session expired message
    if (route.query.session === 'expired') {
      appStore.addNotification({
        type: 'warning',
        title: 'Session Expired',
        message: 'Your previous session expired. Please sign in again.',
        isToast: true
      })
    }

    // Redirect to intended destination
    router.push(decodeURIComponent(redirectPath))
  } catch (error) {
    // Error is handled by the store, we just need to show the notification
    appStore.addNotification({
      type: 'error',
      title: 'Login Failed',
      message: authStore.authError || 'An error occurred during login',
      isToast: true
    })
  }
}

// Auto-clear error when user starts typing
const clearErrorOnInput = () => {
  if (authError.value) {
    authStore.clearError()
  }
}
</script>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-4);
  background: var(--felege-mist);
}
.auth__brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-decoration: none;
}
.auth__geez {
  font-family: var(--felege-font-geez);
  font-weight: 700;
  font-size: 1.75rem;
  line-height: 1.3;
  color: var(--felege-teal);
}
.auth__latin {
  font-family: var(--felege-font-display);
  font-weight: 700;
  font-size: 1.125rem;
  color: var(--felege-ink);
}
.auth__card {
  width: 100%;
  max-width: 400px;
  overflow: hidden;
  background: var(--felege-paper);
  border: 1px solid var(--felege-rule);
  border-radius: var(--felege-radius);
}
.auth__body { padding: var(--space-5); }
.auth__title { font-size: 1.75rem; margin-bottom: var(--space-5); }
.auth__note {
  margin: 0;
  font-size: 0.875rem;
  color: var(--felege-muted);
  text-align: center;
}
</style>

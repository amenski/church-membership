<template>
  <div data-density="comfortable" class="flex min-h-screen flex-col items-center justify-center gap-6 bg-mist px-4 py-6">
    <div class="flex flex-col items-center">
      <span class="font-ethiopic text-2xl leading-tight font-bold text-teal">ፈለገ ሰላም</span>
      <span class="text-sm font-semibold text-ink">Felege Selam</span>
    </div>

    <div class="w-full max-w-sm rounded-md border border-rule bg-paper">
      <div class="p-5">
        <h1 class="mt-0 mb-5 text-xl font-semibold text-ink">Sign in</h1>

        <form @submit.prevent="handleLogin">
          <AlertBanner v-if="sessionExpired" tone="warning" role="status">Your session expired. Sign in again.</AlertBanner>

          <!-- Error Alert -->
          <AlertBanner v-if="authError">{{ authError }}</AlertBanner>

          <!-- Email Field -->
          <div class="mb-4">
            <BaseInput
              id="email"
              v-model="form.email"
              label="Email"
              type="email"
              :error="errors.email"
              autocomplete="username"
              :disabled="isAuthLoading"
              @blur="validateField('email')"
            />
          </div>

          <!-- Password Field -->
          <div class="mb-6">
            <BaseInput
              id="password"
              v-model="form.password"
              label="Password"
              type="password"
              :error="errors.password"
              autocomplete="current-password"
              :disabled="isAuthLoading"
              @blur="validateField('password')"
            />
          </div>

          <!-- Submit Button -->
          <BaseButton type="submit" class="w-full" :disabled="isAuthLoading || !isFormValid">
            <span
              v-if="isAuthLoading"
              class="mr-2 inline-block size-4 rounded-full border-2 border-current border-r-transparent align-[-0.125em] motion-safe:animate-spin"
              aria-hidden="true"
            ></span>
            {{ isAuthLoading ? 'Signing in...' : 'Sign in' }}
          </BaseButton>
        </form>
      </div>
    </div>

    <!-- Registration is disabled: accounts are created by the church office -->
    <p class="m-0 text-center text-sm text-muted">Accounts are set up by the church office.</p>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

// Sent here by a session that ended (api.js, router guard): say so before they sign in
const sessionExpired = router.currentRoute.value.query.session === 'expired'

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
</script>

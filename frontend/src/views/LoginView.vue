<template>
  <div data-density="comfortable" class="tw:flex tw:min-h-screen tw:flex-col tw:items-center tw:justify-center tw:gap-6 tw:bg-mist tw:px-4 tw:py-6">
    <router-link to="/" class="tw:flex tw:flex-col tw:items-center tw:no-underline" aria-label="Felege Selam home">
      <span class="tw:font-ethiopic tw:text-2xl tw:leading-[1.3] tw:font-bold tw:text-teal">ፈለገ ሰላም</span>
      <span class="tw:font-display tw:text-lg tw:font-bold tw:text-ink">Felege Selam</span>
    </router-link>

    <div class="tw:w-full tw:max-w-[400px] tw:overflow-hidden tw:rounded-md tw:border tw:border-rule tw:bg-paper">
      <WovenBand :height="8" />
      <div class="tw:p-6">
        <h1 class="tw:mt-0 tw:mb-6 tw:font-display tw:text-2xl tw:leading-[1.2] tw:font-bold tw:text-ink">Sign in</h1>

        <form @submit.prevent="handleLogin">
          <!-- Error Alert -->
          <AlertBanner v-if="authError">{{ authError }}</AlertBanner>

          <!-- Email Field -->
          <div class="tw:mb-4">
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
          <div class="tw:mb-6">
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
          <BaseButton type="submit" class="tw:w-full" :disabled="isAuthLoading || !isFormValid">
            <span
              v-if="isAuthLoading"
              class="tw:mr-2 tw:inline-block tw:size-4 tw:rounded-full tw:border-2 tw:border-current tw:border-r-transparent tw:align-[-0.125em] tw:motion-safe:animate-spin"
              aria-hidden="true"
            ></span>
            {{ isAuthLoading ? 'Signing in...' : 'Sign in' }}
          </BaseButton>
        </form>
      </div>
    </div>

    <!-- Registration is disabled: accounts are created by the church office -->
    <p class="tw:m-0 tw:text-center tw:text-sm tw:text-muted">Accounts are set up by the church office.</p>
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

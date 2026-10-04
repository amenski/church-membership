<template>
  <div data-density="comfortable" class="flex min-h-screen flex-col bg-mist lg:flex-row">
    <!-- The congregation: a short header below lg, the left half from lg. Nothing in it takes focus. -->
    <section
      aria-label="Felege Selam"
      class="bg-rail px-6 pt-10 pb-7 text-paper lg:flex lg:flex-1 lg:flex-col lg:justify-between lg:p-12 xl:px-[72px] xl:py-16"
    >
      <!-- Decorative: the dues year strip, ten paid, one behind, one due now -->
      <div class="hidden flex-col gap-3.5 lg:flex" aria-hidden="true">
        <div class="grid w-full max-w-[474px] grid-cols-12 gap-1.5">
          <span
            v-for="(state, index) in BRAND_STRIP"
            :key="index"
            :class="['box-border block h-11 rounded-[5px]', state]"
          ></span>
        </div>
        <p class="m-0 max-w-[36ch] text-lg leading-normal text-rail-text">
          One square a month. Solid is paid, hatched is behind, an outline is due now.
        </p>
      </div>

      <div>
        <div class="font-ethiopic text-[36px] leading-tight font-bold lg:text-[56px] lg:leading-[1.2] xl:text-[72px]">ፈለገ ሰላም</div>
        <div class="mt-0.5 text-lg font-medium text-rail-text lg:mt-2 lg:text-2xl lg:text-paper">Felege Selam</div>
        <p class="m-0 mt-5 hidden max-w-[34ch] text-lg leading-relaxed text-rail-text lg:block">
          The church office keeps members, dues, payments and messages in one place.
        </p>
      </div>
    </section>

    <div class="flex flex-1 flex-col items-center gap-4 px-4 pt-6 pb-8 max-lg:justify-start lg:justify-center lg:px-6 lg:py-12">
      <div class="w-full max-w-sm rounded-lg border border-rule bg-paper px-4 py-5 lg:rounded-md lg:p-7">
        <h1 class="mt-0 mb-5 text-2xl font-semibold text-ink">Sign in</h1>

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
              class="max-lg:min-h-12"
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
              class="max-lg:min-h-12"
              :error="errors.password"
              autocomplete="current-password"
              :disabled="isAuthLoading"
              @blur="validateField('password')"
            />
          </div>

          <!-- Submit Button -->
          <BaseButton type="submit" class="w-full max-lg:min-h-12" :disabled="isAuthLoading || !isFormValid">
            <span
              v-if="isAuthLoading"
              class="mr-2 inline-block size-4 rounded-full border-2 border-current border-r-transparent align-[-0.125em] motion-safe:animate-spin"
              aria-hidden="true"
            ></span>
            {{ isAuthLoading ? 'Signing in...' : 'Sign in' }}
          </BaseButton>
        </form>
      </div>

      <!-- Registration is disabled: accounts are created by the church office -->
      <p class="m-0 text-center text-base text-muted lg:text-sm">Accounts are set up by the church office.</p>
    </div>
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
import { SQUARES } from '@/utils/yearStrip'

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

// The brand strip on the left panel: ten paid squares, one behind (hatched), one due now (outlined); decorative
const BRAND_STRIP = [...Array(10).fill('bg-rail-accent'), SQUARES.missed, SQUARES.due]

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

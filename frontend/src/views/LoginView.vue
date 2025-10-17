<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h2 class="auth-title">Sign In</h2>
        <p class="auth-subtitle">Access your Member Tracker account</p>
      </div>

      <form class="auth-form" @submit.prevent="handleLogin">
        <!-- Error Alert -->
        <div v-if="authError" class="alert alert-danger" role="alert">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
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
            placeholder="Enter your email"
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
            placeholder="Enter your password"
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
          class="btn btn-primary w-100 mb-3"
          :disabled="isAuthLoading || !isFormValid"
        >
          <span v-if="isAuthLoading" class="spinner-border spinner-border-sm me-2" role="status"></span>
          {{ isAuthLoading ? 'Signing In...' : 'Sign In' }}
        </button>

        <!-- Links -->
        <div class="auth-links text-center">
          <p class="mb-0">
            Don't have an account?
            <router-link to="/register" class="auth-link">Sign up here</router-link>
          </p>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'

export default {
  name: 'LoginView',
  setup() {
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
          } else if (form.password.length < 6) {
            errors.password = 'Password must be at least 6 characters'
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


    return {
      form,
      errors,
      isAuthLoading,
      authError,
      isFormValid,
      validateField,
      handleLogin,
      clearErrorOnInput
    }
  }
}
</script>

<style scoped>
.auth-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.auth-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  padding: 2.5rem;
  width: 100%;
  max-width: 400px;
}

.auth-header {
  text-align: center;
  margin-bottom: 2rem;
}

.auth-title {
  color: #2c3e50;
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.auth-subtitle {
  color: #6c757d;
  margin-bottom: 0;
}

.auth-form {
  margin-top: 1.5rem;
}

.form-label {
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.5rem;
}

.form-control {
  border: 2px solid #e9ecef;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  transition: all 0.3s ease;
}

.form-control:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 0.2rem rgba(102, 126, 234, 0.25);
}

.form-control.is-invalid {
  border-color: #dc3545;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  padding: 0.75rem;
  font-weight: 600;
  transition: all 0.3s ease;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.auth-links {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e9ecef;
}

.auth-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.3s ease;
}

.auth-link:hover {
  color: #764ba2;
  text-decoration: underline;
}

.alert {
  border-radius: 8px;
  border: none;
  padding: 0.75rem 1rem;
}

.alert-danger {
  background-color: #f8d7da;
  color: #721c24;
}

.spinner-border-sm {
  width: 1rem;
  height: 1rem;
}

/* Responsive design */
@media (max-width: 576px) {
  .auth-card {
    padding: 2rem 1.5rem;
    margin: 1rem;
  }

  .auth-container {
    padding: 10px;
  }
}
</style>

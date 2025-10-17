<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h2 class="auth-title">Create Account</h2>
        <p class="auth-subtitle">Join Member Tracker today</p>
      </div>

      <form class="auth-form" @submit.prevent="handleRegister">
        <!-- Error Alert -->
        <div v-if="authError" class="alert alert-danger" role="alert">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          {{ authError }}
        </div>

        <!-- First Name Field -->
        <div class="mb-3">
          <label for="firstName" class="form-label">First Name</label>
          <input
            id="firstName"
            v-model="form.firstName"
            type="text"
            class="form-control"
            :class="{ 'is-invalid': errors.firstName }"
            placeholder="Enter your first name"
            :disabled="isAuthLoading"
            @blur="validateField('firstName')"
          />
          <div v-if="errors.firstName" class="invalid-feedback">
            {{ errors.firstName }}
          </div>
        </div>

        <!-- Last Name Field -->
        <div class="mb-3">
          <label for="lastName" class="form-label">Last Name</label>
          <input
            id="lastName"
            v-model="form.lastName"
            type="text"
            class="form-control"
            :class="{ 'is-invalid': errors.lastName }"
            placeholder="Enter your last name"
            :disabled="isAuthLoading"
            @blur="validateField('lastName')"
          />
          <div v-if="errors.lastName" class="invalid-feedback">
            {{ errors.lastName }}
          </div>
        </div>

        <!-- Email Field -->
        <div class="mb-3">
          <label for="email" class="form-label">Email Address</label>
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
        <div class="mb-3">
          <label for="password" class="form-label">Password</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            class="form-control"
            :class="{ 'is-invalid': errors.password }"
            placeholder="Create a password"
            :disabled="isAuthLoading"
            @blur="validateField('password')"
          />
          <div v-if="errors.password" class="invalid-feedback">
            {{ errors.password }}
          </div>
          <div class="form-text">
            Password must be at least 6 characters long
          </div>
        </div>

        <!-- Confirm Password Field -->
        <div class="mb-4">
          <label for="confirmPassword" class="form-label">Confirm Password</label>
          <input
            id="confirmPassword"
            v-model="form.confirmPassword"
            type="password"
            class="form-control"
            :class="{ 'is-invalid': errors.confirmPassword }"
            placeholder="Confirm your password"
            :disabled="isAuthLoading"
            @blur="validateField('confirmPassword')"
          />
          <div v-if="errors.confirmPassword" class="invalid-feedback">
            {{ errors.confirmPassword }}
          </div>
        </div>

        <!-- Terms Agreement -->
        <div class="mb-4">
          <div class="form-check">
            <input
              id="agreeTerms"
              v-model="form.agreeTerms"
              class="form-check-input"
              :class="{ 'is-invalid': errors.agreeTerms }"
              type="checkbox"
              :disabled="isAuthLoading"
              @change="validateField('agreeTerms')"
            />
            <label class="form-check-label" for="agreeTerms">
              I agree to the <a href="#" class="auth-link">Terms of Service</a> and <a href="#" class="auth-link">Privacy Policy</a>
            </label>
            <div v-if="errors.agreeTerms" class="invalid-feedback d-block">
              {{ errors.agreeTerms }}
            </div>
          </div>
        </div>

        <!-- Submit Button -->
        <button
          type="submit"
          class="btn btn-primary w-100 mb-3"
          :disabled="isAuthLoading || !isFormValid"
        >
          <span v-if="isAuthLoading" class="spinner-border spinner-border-sm me-2" role="status"></span>
          {{ isAuthLoading ? 'Creating Account...' : 'Create Account' }}
        </button>

        <!-- Links -->
        <div class="auth-links text-center">
          <p class="mb-0">
            Already have an account?
            <router-link to="/login" class="auth-link">Sign in here</router-link>
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
  name: 'RegisterView',
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
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
      agreeTerms: false
    })

    // Validation errors
    const errors = reactive({
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
      agreeTerms: ''
    })

    // Computed properties
    const isAuthLoading = computed(() => authStore.isAuthLoading)
    const authError = computed(() => authStore.authError)
    const isFormValid = computed(() => {
      return form.firstName.trim() &&
             form.lastName.trim() &&
             form.email.trim() &&
             form.password.trim() &&
             form.confirmPassword.trim() &&
             form.agreeTerms &&
             !Object.values(errors).some(error => error)
    })

    // Validation methods
    const validateField = (field) => {
      switch (field) {
        case 'firstName':
          if (!form.firstName.trim()) {
            errors.firstName = 'First name is required'
          } else if (form.firstName.length < 2) {
            errors.firstName = 'First name must be at least 2 characters'
          } else if (form.firstName.length > 50) {
            errors.firstName = 'First name must be less than 50 characters'
          } else {
            errors.firstName = ''
          }
          break

        case 'lastName':
          if (!form.lastName.trim()) {
            errors.lastName = 'Last name is required'
          } else if (form.lastName.length < 2) {
            errors.lastName = 'Last name must be at least 2 characters'
          } else if (form.lastName.length > 50) {
            errors.lastName = 'Last name must be less than 50 characters'
          } else {
            errors.lastName = ''
          }
          break

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
            // If password changes and confirmPassword is set, re-validate confirmPassword
            if (form.confirmPassword) {
              validateField('confirmPassword')
            }
          }
          break

        case 'confirmPassword':
          if (!form.confirmPassword.trim()) {
            errors.confirmPassword = 'Please confirm your password'
          } else if (form.password !== form.confirmPassword) {
            errors.confirmPassword = 'Passwords do not match'
          } else {
            errors.confirmPassword = ''
          }
          break

        case 'agreeTerms':
          if (!form.agreeTerms) {
            errors.agreeTerms = 'You must agree to the terms and conditions'
          } else {
            errors.agreeTerms = ''
          }
          break
      }
    }

    const validateForm = () => {
      Object.keys(form).forEach(field => {
        if (field !== 'agreeTerms' || form.agreeTerms) {
          validateField(field)
        }
      })
      return !Object.values(errors).some(error => error)
    }

    // Registration handler
    const handleRegister = async () => {
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
        const userData = {
          firstName: sanitizeInput(form.firstName),
          lastName: sanitizeInput(form.lastName),
          email: sanitizeInput(form.email),
          password: form.password // Don't sanitize password
        }

        // Call register action
        await authStore.register(userData)

        // Show success notification
        appStore.addNotification({
          type: 'success',
          title: 'Account Created!',
          message: 'Your account has been created successfully. Please sign in.',
          isToast: true
        })

        // Redirect to login page
        router.push('/login')
      } catch (error) {
        // Error is handled by the store, we just need to show the notification
        appStore.addNotification({
          type: 'error',
          title: 'Registration Failed',
          message: authStore.authError || 'An error occurred during registration',
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
      handleRegister,
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
  max-width: 450px;
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

.form-check-input {
  border: 2px solid #e9ecef;
  border-radius: 4px;
}

.form-check-input:checked {
  background-color: #667eea;
  border-color: #667eea;
}

.form-check-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 0.2rem rgba(102, 126, 234, 0.25);
}

.form-check-input.is-invalid {
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

.form-text {
  font-size: 0.875rem;
  color: #6c757d;
  margin-top: 0.25rem;
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

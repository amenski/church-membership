<template>
  <div class="container-fluid py-4">
    <div class="row justify-content-center">
      <div class="col-12 col-lg-8 col-xl-6">
        <!-- Page Header -->
        <div class="d-flex justify-content-between align-items-center mb-4">
          <h1 class="h3 mb-0 text-dark">
            <i class="bi bi-person-circle me-2"></i>My Profile
          </h1>
          <div class="badge bg-primary fs-6">
            <i class="bi bi-shield-check me-1"></i>{{ user.role }}
          </div>
        </div>

        <!-- Loading State -->
        <div v-if="loading" class="text-center py-5">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p class="mt-2 text-muted">Loading profile...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="alert alert-danger d-flex align-items-center" role="alert">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          <div>{{ error }}</div>
        </div>

        <!-- Profile Content -->
        <div v-else>
          <!-- Personal Information Card -->
          <div class="card shadow-sm mb-4">
            <div class="card-header bg-light">
              <h5 class="card-title mb-0">
                <i class="bi bi-person-vcard me-2"></i>Personal Information
              </h5>
            </div>
            <div class="card-body">
              <form @submit.prevent="handleSubmit">
                <div class="row">
                  <!-- Email (Read-only) -->
                  <div class="col-12 mb-3">
                    <label for="email" class="form-label">Email Address</label>
                    <div class="input-group">
                      <span class="input-group-text">
                        <i class="bi bi-envelope"></i>
                      </span>
                      <input
                        id="email"
                        type="email"
                        v-model="user.email"
                        disabled
                        class="form-control"
                      />
                    </div>
                    <div class="form-text text-muted">
                      <i class="bi bi-info-circle me-1"></i>Email cannot be changed
                    </div>
                  </div>

                  <!-- First Name & Last Name -->
                  <div class="col-md-6 mb-3">
                    <label for="firstName" class="form-label">First Name</label>
                    <div class="input-group">
                      <span class="input-group-text">
                        <i class="bi bi-person"></i>
                      </span>
                      <input
                        id="firstName"
                        type="text"
                        v-model="form.firstName"
                        maxlength="50"
                        :disabled="!editing"
                        class="form-control"
                        :class="{ 'is-invalid': formErrors.firstName }"
                      />
                    </div>
                    <div v-if="formErrors.firstName" class="invalid-feedback d-block">
                      {{ formErrors.firstName }}
                    </div>
                  </div>

                  <div class="col-md-6 mb-3">
                    <label for="lastName" class="form-label">Last Name</label>
                    <div class="input-group">
                      <span class="input-group-text">
                        <i class="bi bi-person"></i>
                      </span>
                      <input
                        id="lastName"
                        type="text"
                        v-model="form.lastName"
                        maxlength="50"
                        :disabled="!editing"
                        class="form-control"
                        :class="{ 'is-invalid': formErrors.lastName }"
                      />
                    </div>
                    <div v-if="formErrors.lastName" class="invalid-feedback d-block">
                      {{ formErrors.lastName }}
                    </div>
                  </div>

                  <!-- Phone -->
                  <div class="col-12 mb-3">
                    <label for="phone" class="form-label">Phone Number</label>
                    <div class="input-group">
                      <span class="input-group-text">
                        <i class="bi bi-telephone"></i>
                      </span>
                      <input
                        id="phone"
                        type="tel"
                        v-model="form.phone"
                        :disabled="!editing"
                        class="form-control"
                        :class="{ 'is-invalid': formErrors.phone }"
                        placeholder="e.g., +1234567890"
                      />
                    </div>
                    <div v-if="formErrors.phone" class="invalid-feedback d-block">
                      {{ formErrors.phone }}
                    </div>
                  </div>

                  <!-- Bio -->
                  <div class="col-12 mb-4">
                    <label for="bio" class="form-label">Bio</label>
                    <textarea
                      id="bio"
                      v-model="form.bio"
                      :disabled="!editing"
                      class="form-control"
                      :class="{ 'is-invalid': formErrors.bio }"
                      rows="4"
                      placeholder="Tell us about yourself..."
                    ></textarea>
                    <div class="form-text text-muted">
                      <i class="bi bi-pencil me-1"></i>Write a brief description about yourself
                    </div>
                    <div v-if="formErrors.bio" class="invalid-feedback d-block">
                      {{ formErrors.bio }}
                    </div>
                  </div>
                </div>

                <!-- Action Buttons -->
                <div class="d-flex gap-2">
                  <button
                    v-if="!editing"
                    type="button"
                    @click="startEditing"
                    class="btn btn-primary"
                  >
                    <i class="bi bi-pencil me-1"></i>Edit Profile
                  </button>
                  <template v-else>
                    <button type="submit" class="btn btn-success" :disabled="saving">
                      <i class="bi bi-check-lg me-1"></i>
                      {{ saving ? 'Saving...' : 'Save Changes' }}
                    </button>
                    <button
                      type="button"
                      @click="cancelEditing"
                      class="btn btn-outline-secondary"
                      :disabled="saving"
                    >
                      <i class="bi bi-x-lg me-1"></i>Cancel
                    </button>
                  </template>
                </div>
              </form>
            </div>
          </div>

          <!-- Success Message -->
          <div v-if="successMessage" class="alert alert-success alert-dismissible fade show" role="alert">
            <i class="bi bi-check-circle-fill me-2"></i>
            <strong>Success!</strong> {{ successMessage }}
            <button type="button" class="btn-close" @click="successMessage = null"></button>
          </div>

          <!-- Account Information Card -->
          <div class="card shadow-sm">
            <div class="card-header bg-light">
              <h5 class="card-title mb-0">
                <i class="bi bi-shield-lock me-2"></i>Account Information
              </h5>
            </div>
            <div class="card-body">
              <div class="d-flex gap-2">
                <button
                  type="button"
                  class="btn btn-outline-primary btn-sm"
                  data-bs-toggle="modal"
                  data-bs-target="#changePasswordModal"
                >
                  <i class="bi bi-key me-1"></i>Change Password
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Change Password Modal -->
    <div class="modal fade" id="changePasswordModal" tabindex="-1" aria-labelledby="changePasswordModalLabel" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="changePasswordModalLabel">
              <i class="bi bi-key me-2"></i>Change Password
            </h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="handlePasswordChange">
              <div v-if="passwordServerError" class="alert alert-danger" role="alert">
                {{ passwordServerError }}
              </div>
              <div class="mb-3">
                <label for="currentPassword" class="form-label">Current Password</label>
                <input
                  id="currentPassword"
                  type="password"
                  v-model="passwordForm.currentPassword"
                  class="form-control"
                  :class="{ 'is-invalid': passwordErrors.currentPassword }"
                  placeholder="Enter your current password"
                />
                <div v-if="passwordErrors.currentPassword" class="invalid-feedback">
                  {{ passwordErrors.currentPassword }}
                </div>
              </div>

              <div class="mb-3">
                <label for="newPassword" class="form-label">New Password</label>
                <input
                  id="newPassword"
                  type="password"
                  v-model="passwordForm.newPassword"
                  class="form-control"
                  :class="{ 'is-invalid': passwordErrors.newPassword }"
                  placeholder="Enter new password"
                />
                <div class="form-text">
                  <i class="bi bi-info-circle me-1"></i>8 to 72 characters with an uppercase letter, a lowercase letter, a digit and a special character
                </div>
                <div v-if="passwordErrors.newPassword" class="invalid-feedback">
                  {{ passwordErrors.newPassword }}
                </div>
              </div>

              <div class="mb-3">
                <label for="confirmPassword" class="form-label">Confirm New Password</label>
                <input
                  id="confirmPassword"
                  type="password"
                  v-model="passwordForm.confirmPassword"
                  class="form-control"
                  :class="{ 'is-invalid': passwordErrors.confirmPassword }"
                  placeholder="Confirm new password"
                />
                <div v-if="passwordErrors.confirmPassword" class="invalid-feedback">
                  {{ passwordErrors.confirmPassword }}
                </div>
              </div>

              <div class="d-flex gap-2 justify-content-end">
                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancel</button>
                <button type="submit" class="btn btn-primary" :disabled="changingPassword">
                  <i class="bi bi-check-lg me-1"></i>
                  {{ changingPassword ? 'Changing...' : 'Change Password' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Modal } from 'bootstrap'
import { useAuthStore, useAppStore } from '@/stores/index.js'
import apiService from '@/services/api.js'
import { validateNewPassword } from '@/utils/passwordRules.js'
import { isValidPhone } from '@/utils/phoneRules.js'

export default {
  name: 'ProfileView',
  setup() {
    const authStore = useAuthStore()
    const appStore = useAppStore()
    const user = ref(authStore.user || {})
    const form = ref({
      firstName: '',
      lastName: '',
      phone: '',
      bio: ''
    })
    const formErrors = ref({
      firstName: '',
      lastName: '',
      phone: '',
      bio: ''
    })
    const editing = ref(false)
    const loading = ref(true)
    const saving = ref(false)
    const error = ref(null)
    const successMessage = ref(null)

    // Password change functionality
    const changingPassword = ref(false)
    const passwordServerError = ref('')
    const passwordForm = ref({
      currentPassword: '',
      newPassword: '',
      confirmPassword: ''
    })
    const passwordErrors = ref({
      currentPassword: '',
      newPassword: '',
      confirmPassword: ''
    })

    // Form validation
    const validateForm = () => {
      let isValid = true
      formErrors.value = { firstName: '', lastName: '', phone: '', bio: '' }

      // First name validation
      if (!form.value.firstName?.trim()) {
        formErrors.value.firstName = 'First name is required'
        isValid = false
      }

      // Last name validation
      if (!form.value.lastName?.trim()) {
        formErrors.value.lastName = 'Last name is required'
        isValid = false
      }

      // Phone validation (optional but must match the server's pattern if provided)
      if (!isValidPhone(form.value.phone)) {
        formErrors.value.phone = 'Please enter a valid phone number'
        isValid = false
      }

      // Bio validation (optional but limit length)
      if (form.value.bio && form.value.bio.length > 500) {
        formErrors.value.bio = 'Bio must be less than 500 characters'
        isValid = false
      }

      return isValid
    }

    const loadProfile = async () => {
      try {
        loading.value = true
        error.value = null
        const data = await apiService.getCurrentUser()
        user.value = data
        form.value = {
          firstName: data.firstName || '',
          lastName: data.lastName || '',
          phone: data.phone || '',
          bio: data.bio || ''
        }
      } catch (err) {
        error.value = 'Failed to load profile'
        console.error('Error loading profile:', err)
      } finally {
        loading.value = false
      }
    }

    const startEditing = () => {
      editing.value = true
      successMessage.value = null
      formErrors.value = { firstName: '', lastName: '', phone: '', bio: '' }
    }

    const cancelEditing = () => {
      editing.value = false
      successMessage.value = null
      form.value = {
        firstName: user.value.firstName || '',
        lastName: user.value.lastName || '',
        phone: user.value.phone || '',
        bio: user.value.bio || ''
      }
      formErrors.value = { firstName: '', lastName: '', phone: '', bio: '' }
    }

    const handleSubmit = async () => {
      // Validate form before submission
      if (!validateForm()) {
        return
      }

      try {
        saving.value = true
        error.value = null
        successMessage.value = null

        const data = await apiService.updateProfile(form.value)
        user.value = data
        authStore.user = data
        editing.value = false
        successMessage.value = 'Profile updated successfully!'

        setTimeout(() => {
          successMessage.value = null
        }, 5000)
      } catch (err) {
        error.value = 'Failed to update profile'
        console.error('Error updating profile:', err)
      } finally {
        saving.value = false
      }
    }

    // Password validation
    const validatePasswordForm = () => {
      let isValid = true
      passwordErrors.value = { currentPassword: '', newPassword: '', confirmPassword: '' }

      // Current password validation
      if (!passwordForm.value.currentPassword) {
        passwordErrors.value.currentPassword = 'Current password is required'
        isValid = false
      }

      // New password validation
      if (!passwordForm.value.newPassword) {
        passwordErrors.value.newPassword = 'New password is required'
        isValid = false
      } else {
        const rule = validateNewPassword(passwordForm.value.newPassword)
        if (rule) {
          passwordErrors.value.newPassword = rule
          isValid = false
        }
      }

      // Confirm password validation
      if (!passwordForm.value.confirmPassword) {
        passwordErrors.value.confirmPassword = 'Please confirm your new password'
        isValid = false
      } else if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
        passwordErrors.value.confirmPassword = 'Passwords do not match'
        isValid = false
      }

      return isValid
    }

    // Reset password form
    const resetPasswordForm = () => {
      passwordForm.value = {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      passwordErrors.value = {
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      passwordServerError.value = ''
    }

    // Handle password change
    const handlePasswordChange = async () => {
      if (!validatePasswordForm()) {
        return
      }

      try {
        changingPassword.value = true
        passwordServerError.value = ''

        await apiService.changePassword({
          currentPassword: passwordForm.value.currentPassword,
          newPassword: passwordForm.value.newPassword
        })

        resetPasswordForm()
        Modal.getOrCreateInstance(document.getElementById('changePasswordModal')).hide()
        appStore.addNotification({
          type: 'success',
          title: 'Password changed',
          message: 'Your password was changed.',
          isToast: true
        })
      } catch (err) {
        // The API interceptor puts the server's reason in err.message
        passwordServerError.value = err.fieldErrors?.[0]?.message || err.message || 'Failed to change password'
        console.error('Error changing password:', err)
      } finally {
        changingPassword.value = false
      }
    }

    onMounted(() => {
      loadProfile()
      document.getElementById('changePasswordModal')?.addEventListener('hidden.bs.modal', resetPasswordForm)
    })

    onBeforeUnmount(() => {
      document.getElementById('changePasswordModal')?.removeEventListener('hidden.bs.modal', resetPasswordForm)
    })

    return {
      user,
      form,
      formErrors,
      editing,
      loading,
      saving,
      error,
      successMessage,
      changingPassword,
      passwordServerError,
      passwordForm,
      passwordErrors,
      startEditing,
      cancelEditing,
      handleSubmit,
      handlePasswordChange
    }
  }
}
</script>

<style scoped>
.profile-container {
  max-width: 800px;
  margin: 2rem auto;
  padding: 0 1rem;
}

.profile-card {
  background: white;
  border-radius: 8px;
  padding: 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

h1 {
  margin-bottom: 2rem;
  color: #333;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

label {
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: #555;
}

.form-text {
  margin-top: 0.25rem;
  color: #666;
  font-size: 0.875rem;
}

textarea.form-control {
  resize: vertical;
  min-height: 100px;
}

.form-actions {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.error {
  padding: 1rem;
  background-color: #ffebee;
  color: #c62828;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.success-message {
  padding: 1rem;
  background-color: #e8f5e9;
  color: #2e7d32;
  border-radius: 4px;
  margin-top: 1rem;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>

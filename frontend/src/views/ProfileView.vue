<template>
  <div>
    <PageHead title="Profile" lead="Your details and password." />

    <div class="tw:max-w-160">
      <p v-if="loading" class="tw:m-0 tw:py-4 tw:text-(length:--text-body) tw:text-muted" role="status">Loading profile...</p>

      <AlertBanner v-else-if="error">
        <div class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
          <span>{{ error }}. Check your connection and try again.</span>
          <BaseButton variant="secondary" size="sm" @click="loadProfile">Try again</BaseButton>
        </div>
      </AlertBanner>

      <template v-else>
        <section :class="CARD" aria-labelledby="details-title">
          <SectionTitle id="details-title">Your details</SectionTitle>

          <dl class="tw:m-0 tw:mb-1 tw:grid tw:grid-cols-[auto_1fr] tw:gap-x-6 tw:gap-y-1 tw:text-(length:--text-body) tw:leading-(--lh-body)">
            <dt class="tw:font-normal tw:text-muted">Email</dt>
            <dd class="tw:m-0 tw:min-w-0 tw:[overflow-wrap:anywhere]">{{ user.email }}</dd>
            <dt class="tw:font-normal tw:text-muted">Role</dt>
            <dd class="tw:m-0 tw:font-medium">{{ roleLabel }}</dd>
          </dl>
          <p class="tw:mt-0 tw:mb-6 tw:text-(length:--text-label) tw:leading-(--lh-label) tw:text-muted">Your email cannot be changed here.</p>

          <AlertBanner v-if="successMessage" tone="success">{{ successMessage }}</AlertBanner>
          <AlertBanner v-if="saveError">{{ saveError }}</AlertBanner>

          <form class="tw:flex tw:flex-col tw:gap-4" novalidate @submit.prevent="handleSubmit">
            <div class="tw:grid tw:grid-cols-1 tw:gap-4 tw:sm:grid-cols-2">
              <BaseInput id="firstName" v-model="form.firstName" label="First name" maxlength="50" autocomplete="given-name" :error="formErrors.firstName" />
              <BaseInput id="lastName" v-model="form.lastName" label="Last name" maxlength="50" autocomplete="family-name" :error="formErrors.lastName" />
            </div>
            <BaseInput id="phone" v-model="form.phone" label="Phone" type="tel" autocomplete="tel" hint="Optional. 10 digits or more." :error="formErrors.phone" />
            <div>
              <label for="bio" :class="LABEL">Bio</label>
              <textarea
                id="bio"
                v-model="form.bio"
                rows="4"
                :aria-invalid="formErrors.bio ? 'true' : undefined"
                aria-describedby="bio-count bio-error"
                :class="[
                  'tw:block tw:w-full tw:resize-y tw:rounded-md tw:border tw:bg-paper tw:px-3 tw:py-2 tw:text-lg tw:leading-normal tw:text-ink',
                  'tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal',
                  formErrors.bio ? 'tw:border-clay' : 'tw:border-field'
                ]"
              ></textarea>
              <div class="tw:mt-1 tw:flex tw:justify-between tw:gap-4 tw:text-[0.9375rem]">
                <p v-if="formErrors.bio" id="bio-error" class="tw:m-0 tw:text-clay">{{ formErrors.bio }}</p>
                <span v-else id="bio-error"></span>
                <span id="bio-count" :class="['tw:tabular-nums', form.bio.length > BIO_MAX ? 'tw:text-clay' : 'tw:text-muted']">{{ form.bio.length }} of {{ BIO_MAX }}</span>
              </div>
            </div>
            <div>
              <BaseButton type="submit" :disabled="saving" :aria-busy="saving ? 'true' : undefined" class="tw:min-h-(--control-primary-h) tw:max-sm:w-full">
                {{ saving ? 'Saving...' : 'Save changes' }}
              </BaseButton>
            </div>
          </form>
        </section>

        <section :class="CARD" aria-labelledby="password-title">
          <SectionTitle id="password-title">Password</SectionTitle>
          <p class="tw:mt-0 tw:mb-4 tw:text-(length:--text-body) tw:leading-(--lh-body)">Use at least 8 characters with an uppercase letter, a lowercase letter, a number and a special character.</p>
          <BaseButton variant="secondary" class="tw:min-h-(--control-h) tw:max-sm:w-full" @click="openPasswordDialog">Change password</BaseButton>
        </section>
      </template>
    </div>

    <BaseModal v-model="passwordOpen" title="Change password" size="md">
      <AlertBanner v-if="passwordServerError">{{ passwordServerError }}</AlertBanner>
      <form id="password-form" class="tw:flex tw:flex-col tw:gap-4" novalidate @submit.prevent="handlePasswordChange">
        <BaseInput id="currentPassword" v-model="passwordForm.currentPassword" label="Current password" type="password" autocomplete="current-password" :error="passwordErrors.currentPassword" />
        <BaseInput
          id="newPassword"
          v-model="passwordForm.newPassword"
          label="New password"
          type="password"
          autocomplete="new-password"
          :hint="passwordErrors.newPassword ? '' : PASSWORD_RULE_MESSAGE"
          :error="passwordErrors.newPassword"
        />
        <BaseInput id="confirmPassword" v-model="passwordForm.confirmPassword" label="Confirm new password" type="password" autocomplete="new-password" :error="passwordErrors.confirmPassword" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="changingPassword" @click="passwordOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="password-form" :disabled="changingPassword" :aria-busy="changingPassword ? 'true' : undefined">
          {{ changingPassword ? 'Changing...' : 'Change password' }}
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script>
import { ref, computed, watch, onMounted } from 'vue'
import { useAuthStore, useAppStore } from '@/stores/index.js'
import apiService from '@/services/api.js'
import { validateNewPassword, PASSWORD_RULE_MESSAGE } from '@/utils/passwordRules.js'
import { isValidPhone } from '@/utils/phoneRules.js'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import PageHead from '@/components/PageHead.vue'
import SectionTitle from '@/components/SectionTitle.vue'

const BIO_MAX = 500
const CARD = 'tw:mb-6 tw:rounded-md tw:border tw:border-rule tw:bg-paper tw:p-(--card-pad)'
const LABEL = 'tw:mb-1 tw:inline-block tw:text-base tw:font-medium tw:text-ink'

export default {
  name: 'ProfileView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, PageHead, SectionTitle },
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
    const loading = ref(true)
    const saving = ref(false)
    const error = ref(null)
    const saveError = ref('')
    const successMessage = ref(null)
    const passwordOpen = ref(false)

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
      if (form.value.bio && form.value.bio.length > BIO_MAX) {
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

    const handleSubmit = async () => {
      // Validate form before submission
      if (!validateForm()) {
        return
      }

      try {
        saving.value = true
        saveError.value = ''
        successMessage.value = null

        const data = await apiService.updateProfile(form.value)
        user.value = data
        authStore.user = data
        successMessage.value = 'Profile updated successfully!'

        setTimeout(() => {
          successMessage.value = null
        }, 5000)
      } catch (err) {
        // Server field errors go under their field; anything else in the card's banner
        const rest = []
        for (const { field, message } of err.fieldErrors || []) {
          if (field in formErrors.value && !formErrors.value[field]) formErrors.value[field] = message
          else rest.push(message)
        }
        if (!err.fieldErrors?.length) rest.push('Failed to update profile')
        if (rest.length) saveError.value = rest.join(' ')
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
      passwordServerError.value = ''
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
        passwordOpen.value = false
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

    const openPasswordDialog = () => {
      resetPasswordForm()
      passwordOpen.value = true
    }

    // Closing the dialog by any route (Cancel, Escape, backdrop, success) clears it
    watch(passwordOpen, (open) => {
      if (!open) resetPasswordForm()
    })

    const roleLabel = computed(() => {
      const role = user.value?.role || ''
      return role.charAt(0) + role.slice(1).toLowerCase()
    })

    onMounted(loadProfile)

    return {
      user,
      form,
      formErrors,
      loading,
      saving,
      error,
      saveError,
      successMessage,
      passwordOpen,
      roleLabel,
      BIO_MAX,
      LABEL,
      CARD,
      PASSWORD_RULE_MESSAGE,
      changingPassword,
      passwordServerError,
      passwordForm,
      passwordErrors,
      loadProfile,
      openPasswordDialog,
      handleSubmit,
      handlePasswordChange
    }
  }
}
</script>

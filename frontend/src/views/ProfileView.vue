<template>
  <div>
    <PageHead :title="$t('nav.profile')" :lead="$t('profile.lead')" />

    <div class="max-w-160">
      <p v-if="loading" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">{{ $t('profile.loading') }}</p>

      <AlertBanner v-else-if="error">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span>{{ error }} {{ $t('common.checkConnection') }}</span>
          <BaseButton variant="secondary" size="sm" @click="loadProfile">{{ $t('common.tryAgain') }}</BaseButton>
        </div>
      </AlertBanner>

      <template v-else>
        <section :class="[CARD, 'mb-6']" aria-labelledby="details-title">
          <SectionTitle id="details-title">{{ $t('profile.yourDetails') }}</SectionTitle>

          <dl class="m-0 mb-1 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-(length:--text-body) leading-(--lh-body)">
            <dt class="font-normal text-muted">{{ $t('profile.email') }}</dt>
            <dd class="m-0 min-w-0 [overflow-wrap:anywhere]">{{ user.email }}</dd>
            <dt class="font-normal text-muted">{{ $t('profile.role') }}</dt>
            <dd class="m-0 font-medium">{{ roleLabel }}</dd>
          </dl>
          <p class="mt-0 mb-6 text-(length:--text-label) leading-(--lh-label) text-muted">{{ $t('profile.emailNote') }}</p>

          <AlertBanner v-if="successMessage" tone="success">{{ successMessage }}</AlertBanner>
          <AlertBanner v-if="saveError">{{ saveError }}</AlertBanner>

          <form class="flex flex-col gap-4" novalidate @submit.prevent="handleSubmit">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <BaseInput id="firstName" v-model="form.firstName" :label="$t('profile.firstName')" maxlength="50" autocomplete="given-name" :error="formErrors.firstName" />
              <BaseInput id="lastName" v-model="form.lastName" :label="$t('profile.lastName')" maxlength="50" autocomplete="family-name" :error="formErrors.lastName" />
            </div>
            <BaseInput id="phone" v-model="form.phone" :label="$t('profile.phone')" type="tel" autocomplete="tel" :hint="$t('profile.phoneHint')" :error="formErrors.phone" />
            <div>
              <label for="bio" :class="LABEL">{{ $t('profile.bio') }}</label>
              <textarea
                id="bio"
                v-model="form.bio"
                rows="4"
                :aria-invalid="formErrors.bio ? 'true' : undefined"
                aria-describedby="bio-count bio-error"
                :class="[
                  'block w-full resize-y rounded-md border bg-paper px-3 py-2 text-lg leading-normal text-ink',
                  'focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal',
                  formErrors.bio ? 'border-clay' : 'border-field'
                ]"
              ></textarea>
              <div class="mt-1 flex justify-between gap-4 text-[0.9375rem]">
                <p v-if="formErrors.bio" id="bio-error" class="m-0 text-clay">{{ formErrors.bio }}</p>
                <span v-else id="bio-error"></span>
                <span id="bio-count" :class="['tabular-nums', form.bio.length > BIO_MAX ? 'text-clay' : 'text-muted']">{{ $t('profile.bioCount', { n: form.bio.length, max: BIO_MAX }) }}</span>
              </div>
            </div>
            <div>
              <BaseButton type="submit" :disabled="saving" :aria-busy="saving ? 'true' : undefined" class="max-sm:w-full">
                {{ saving ? $t('common.saving') : $t('common.saveChanges') }}
              </BaseButton>
            </div>
          </form>
        </section>

        <section :class="[CARD, 'mb-6']" aria-labelledby="password-title">
          <SectionTitle id="password-title">{{ $t('profile.password') }}</SectionTitle>
          <p class="mt-0 mb-4 text-(length:--text-body) leading-(--lh-body)">{{ $t('profile.passwordRules') }}</p>
          <BaseButton variant="secondary" class="max-sm:w-full" @click="openPasswordDialog">{{ $t('profile.changePassword') }}</BaseButton>
        </section>

        <section :class="[CARD, 'mb-6']" aria-labelledby="language-title">
          <SectionTitle id="language-title">{{ $t('common.language') }}</SectionTitle>
          <p class="mt-0 mb-3 text-(length:--text-body) leading-(--lh-body) text-muted">{{ $t('profile.languageNote') }}</p>
          <LanguageSwitcher />
        </section>
      </template>
    </div>

    <BaseModal v-model="passwordOpen" :title="$t('profile.changePassword')" size="md">
      <AlertBanner v-if="passwordServerError">{{ passwordServerError }}</AlertBanner>
      <form id="password-form" class="flex flex-col gap-4" novalidate @submit.prevent="handlePasswordChange">
        <BaseInput id="currentPassword" v-model="passwordForm.currentPassword" :label="$t('profile.currentPassword')" type="password" autocomplete="current-password" :error="passwordErrors.currentPassword" />
        <BaseInput
          id="newPassword"
          v-model="passwordForm.newPassword"
          :label="$t('profile.newPassword')"
          type="password"
          autocomplete="new-password"
          :hint="passwordErrors.newPassword ? '' : $t(PASSWORD_RULE_KEY)"
          :error="passwordErrors.newPassword"
        />
        <BaseInput id="confirmPassword" v-model="passwordForm.confirmPassword" :label="$t('profile.confirmPassword')" type="password" autocomplete="new-password" :error="passwordErrors.confirmPassword" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="changingPassword" @click="passwordOpen = false">{{ $t('common.cancel') }}</BaseButton>
        <BaseButton type="submit" form="password-form" :disabled="changingPassword" :aria-busy="changingPassword ? 'true' : undefined">
          {{ changingPassword ? $t('profile.changing') : $t('profile.changePassword') }}
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script>
import { ref, computed, watch, onMounted } from 'vue'
import { useAuthStore, useAppStore } from '@/stores/index.js'
import { useI18n } from 'vue-i18n'
import apiService from '@/services/api.js'
import { validateNewPassword, PASSWORD_RULE_KEY } from '@/utils/passwordRules.js'
import { isValidPhone } from '@/utils/phoneRules.js'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import PageHead from '@/components/PageHead.vue'
import SectionTitle from '@/components/SectionTitle.vue'

import { CARD, LABEL } from '@/ui/classes'

const BIO_MAX = 500

export default {
  name: 'ProfileView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, LanguageSwitcher, PageHead, SectionTitle },
  setup() {
    const { t } = useI18n()
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
        formErrors.value.firstName = t('validation.firstNameRequired')
        isValid = false
      }

      // Last name validation
      if (!form.value.lastName?.trim()) {
        formErrors.value.lastName = t('validation.lastNameRequired')
        isValid = false
      }

      // Phone validation (optional but must match the server's pattern if provided)
      if (!isValidPhone(form.value.phone)) {
        formErrors.value.phone = t('validation.phoneInvalid')
        isValid = false
      }

      // Bio validation (optional but limit length)
      if (form.value.bio && form.value.bio.length > BIO_MAX) {
        formErrors.value.bio = t('validation.bioTooLong')
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
        error.value = t('profile.loadFailed')
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
        successMessage.value = t('profile.updated')

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
        if (!err.fieldErrors?.length) rest.push(t('profile.updateFailed'))
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
        passwordErrors.value.currentPassword = t('validation.currentPasswordRequired')
        isValid = false
      }

      // New password validation
      if (!passwordForm.value.newPassword) {
        passwordErrors.value.newPassword = t('validation.newPasswordRequired')
        isValid = false
      } else {
        const rule = validateNewPassword(passwordForm.value.newPassword)
        if (rule) {
          passwordErrors.value.newPassword = t(rule)
          isValid = false
        }
      }

      // Confirm password validation
      if (!passwordForm.value.confirmPassword) {
        passwordErrors.value.confirmPassword = t('validation.confirmPasswordRequired')
        isValid = false
      } else if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
        passwordErrors.value.confirmPassword = t('validation.passwordsMismatch')
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
          title: t('profile.passwordChangedTitle'),
          message: t('profile.passwordChangedMessage'),
          isToast: true
        })
      } catch (err) {
        // The API interceptor puts the server's reason in err.message
        passwordServerError.value = err.fieldErrors?.[0]?.message || err.message || t('profile.passwordChangeFailed')
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

    // ADMIN -> roles.admin, and anything unknown falls back to the raw role
    const roleLabel = computed(() => {
      const role = user.value?.role || ''
      if (!role) return ''
      const key = `roles.${role.toLowerCase()}`
      const label = t(key)
      return label === key ? role.charAt(0) + role.slice(1).toLowerCase() : label
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
      PASSWORD_RULE_KEY,
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

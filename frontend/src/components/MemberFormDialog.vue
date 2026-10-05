<template>
  <BaseModal :model-value="modelValue" :title="member ? $t('members.formTitleEdit') : $t('members.addMember')" size="md" @update:model-value="close">
    <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
    <form id="member-form" class="flex flex-col gap-4" novalidate @submit.prevent="saveMember">
      <BaseInput id="member-name" v-model="memberForm.name" :label="$t('members.formName')" autocomplete="off" :error="formErrors.name" />
      <BaseInput id="member-email" v-model="memberForm.email" :label="$t('members.formEmail')" type="email" autocomplete="off" :hint="$t('members.formEmailHint')" :error="formErrors.email" />
      <BaseInput id="member-phone" v-model="memberForm.phone" :label="$t('members.formPhone')" type="tel" autocomplete="off" :hint="$t('common.phoneHint')" :error="formErrors.phone" />
      <BaseInput id="member-joined" v-model="memberForm.joinDate" :label="$t('members.formJoined')" type="date" :max="today" :error="formErrors.joinDate" />
      <BaseSelect
        id="member-household"
        :label="$t('members.colHousehold')"
        :model-value="memberForm.householdId"
        :disabled="householdsFailed"
        :hint="householdsFailed ? $t('members.formHouseholdFailed') : $t('members.formHouseholdHint')"
        :error="formErrors.householdId"
        @update:model-value="chooseHousehold"
      >
        <option value="">{{ $t('common.none') }}</option>
        <option v-for="household in householdOptions" :key="household.id" :value="String(household.id)">{{ household.name }}</option>
      </BaseSelect>
      <BaseSelect id="member-status" v-model="memberForm.status" :label="$t('members.formStatus')" :hint="statusHint">
        <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ $t(option.labelKey) }}</option>
      </BaseSelect>
    </form>
    <template #footer>
      <BaseButton variant="secondary" :disabled="saving" @click="close(false)">{{ $t('common.cancel') }}</BaseButton>
      <BaseButton type="submit" form="member-form" :disabled="saving" :aria-busy="saving ? 'true' : undefined">
        {{ saving ? $t('common.saving') : member ? $t('common.saveChanges') : $t('members.addMember') }}
      </BaseButton>
    </template>
  </BaseModal>
</template>

<script>
// The Add member / Edit member dialog, shared by the Members list and a member's own page. It owns the form, the
// validation, the household list and the save; the screen that opens it reloads its data on `saved`.
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { isValidEmail, localISODate } from '@/utils'
import { buildMemberRequest } from '@/utils/memberPayload'
import { NEW_MEMBER_STATUS_OPTIONS, STATUS_OPTIONS } from '@/utils/memberStatus'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'

const EMPTY_ERRORS = { name: '', email: '', phone: '', joinDate: '', householdId: '' }

export default {
  name: 'MemberFormDialog',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect },
  props: {
    modelValue: { type: Boolean, default: false },
    // the member being edited; null adds a new one
    member: { type: Object, default: null },
    // "Change status..." wants the Status select focused instead of the first field
    focusStatus: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'saved'],
  setup() {
    return { appStore: useAppStore() }
  },
  data() {
    return {
      memberForm: { name: '', email: '', phone: '', joinDate: '', status: 'MEMBER', householdId: '', householdTouched: false },
      households: [],
      householdsFailed: false,
      saving: false,
      formError: '',
      formErrors: { ...EMPTY_ERRORS },
      today: localISODate()
    }
  },
  computed: {
    statusOptions() {
      return this.member ? STATUS_OPTIONS : NEW_MEMBER_STATUS_OPTIONS
    },
    // the loaded households, plus the member's current one when the list lacks it (so the select never shows a blank)
    householdOptions() {
      const current = this.member
      if (current?.householdId && !this.households.some(household => household.id === current.householdId)) {
        return [...this.households, { id: current.householdId, name: current.householdName || this.$t('members.formCurrentHousehold') }]
      }
      return this.households
    },
    statusHint() {
      return this.member
        ? this.$t('members.formStatusHintReactivate')
        : this.$t('members.formStatusHintNew')
    }
  },
  watch: {
    // each time the dialog opens it starts from the member (or from an empty form)
    modelValue(open) {
      if (open) this.open()
    }
  },
  mounted() {
    if (this.modelValue) this.open()
  },
  methods: {
    open() {
      const member = this.member
      this.memberForm = member
        ? {
            name: member.name || '',
            email: member.email || '',
            phone: member.phone || '',
            joinDate: member.joinDate || '',
            status: member.status || 'MEMBER',
            householdId: member.householdId ? String(member.householdId) : '',
            householdTouched: false
          }
        : { name: '', email: '', phone: '', joinDate: localISODate(), status: 'MEMBER', householdId: '', householdTouched: false }
      this.today = localISODate()
      this.resetFormErrors()
      this.loadHouseholds()
      // the dialog focuses the first field when it opens; "Change status..." wants the select instead
      if (member && this.focusStatus) setTimeout(() => document.getElementById('member-status')?.focus(), 0)
    },
    close(open) {
      this.$emit('update:modelValue', open)
    },
    resetFormErrors() {
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
    },
    // for the Household select; a failure only disables the select, the rest of the form still saves
    async loadHouseholds() {
      this.householdsFailed = false
      try {
        const data = await api.getHouseholds()
        this.households = Array.isArray(data) ? data : []
      } catch (error) {
        console.error('Error loading households:', error)
        this.householdsFailed = true
      }
    },
    chooseHousehold(value) {
      this.memberForm.householdId = value
      this.memberForm.householdTouched = true
    },
    validateForm() {
      this.formErrors = { ...EMPTY_ERRORS }
      if (!this.memberForm.name.trim()) this.formErrors.name = 'Enter the member\'s name.'
      const email = this.memberForm.email.trim()
      if (email && !isValidEmail(email)) this.formErrors.email = 'Enter a valid email address, like name@example.com.'
      return !this.formErrors.name && !this.formErrors.email
    },
    // Put each server field error under its field; anything else goes in the banner
    showSaveError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        if (field in EMPTY_ERRORS && !this.formErrors[field]) this.formErrors[field] = message
        else rest.push(message)
      }
      const message = error.message || this.$t('common.requestFailed')
      if (!fieldErrors.length) rest.push(message)
      if (rest.length) {
        this.formError = rest.join(' ')
        // The shared API handler already shows an "Access Denied" toast for 403
        if (error.response?.status !== 403) this.notify('error', this.$t('members.couldNotSave'), message)
      }
    },
    async saveMember() {
      this.resetFormErrors()
      if (!this.validateForm()) return
      this.saving = true
      try {
        const request = buildMemberRequest(this.memberForm)
        const editing = !!this.member
        // a new member with no household sends nothing; only an edit sends null to leave one
        if (!editing && request.householdId === null) delete request.householdId
        if (editing) {
          await api.updateMember(this.member.id, request)
        } else {
          await api.createMember(request)
        }
        this.$emit('saved', { editing, name: request.name })
        this.close(false)
        this.notify('success', editing ? this.$t('members.saved') : this.$t('members.addedMember'), request.name)
      } catch (error) {
        console.error('Error saving member:', error)
        this.showSaveError(error)
      } finally {
        this.saving = false
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    }
  }
}
</script>

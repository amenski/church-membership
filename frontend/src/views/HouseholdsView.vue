<template>
  <div>
    <PageHead title="Households" lead="Families and shared addresses, and who lives in each.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton @click="showAddModal">
          <Icon name="plus" :size="16" class="mr-1.5" />Add household
        </BaseButton>
      </template>
    </PageHead>

    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The household list did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadHouseholds">Try again</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading households...</p>

    <!-- Search -->
    <form v-if="households.length" class="mb-6" role="search" aria-label="Search households" @submit.prevent>
      <div class="md:w-80">
        <label for="household-search" :class="LABEL">Search</label>
        <input id="household-search" v-model="search" type="search" placeholder="Search by household name" autocomplete="off" :class="CONTROL">
      </div>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !households.length">
      <EmptyNote>No households yet. <template v-if="authStore.isStaff">Add the first household, then choose it when you add or edit a member.</template><template v-else>A staff member can add the first one.</template></EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="showAddModal">Add household</BaseButton>
    </div>
    <div v-else-if="households.length && !visibleHouseholds.length">
      <EmptyNote>No household matches "{{ search.trim() }}".</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="search = ''">Clear search</BaseButton>
    </div>

    <template v-if="visibleHouseholds.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ visibleHouseholds.length !== households.length ? `${visibleHouseholds.length} of ${households.length} households` : `${households.length} ${households.length === 1 ? 'household' : 'households'}` }}
      </p>

      <!-- md and up: ruled table -->
      <table :class="TABLE">
        <caption class="sr-only">Households</caption>
        <thead>
          <tr class="border-b border-rule">
            <th scope="col" :class="TH">Name</th>
            <th scope="col" :class="TH">City</th>
            <th scope="col" :class="[TH, 'w-32']">Members</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="household in visibleHouseholds" :key="household.id" class="h-(--row-h) border-b border-rule">
            <td :class="[TD, 'max-w-0 w-[50%]']">
              <button type="button" :class="OPEN" @click="openDetail(household)">{{ household.name }}</button>
            </td>
            <td :class="[TD, 'max-w-0 [overflow-wrap:anywhere]']">
              <template v-if="household.city">{{ household.city }}</template>
              <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">No city</span></span>
            </td>
            <td :class="TD">{{ household.memberCount }}</td>
          </tr>
        </tbody>
      </table>

      <!-- Below md: the same rows, stacked -->
      <ul class="m-0 list-none border-t border-rule p-0 md:hidden">
        <li v-for="household in visibleHouseholds" :key="household.id" class="border-b border-rule">
          <button type="button" class="flex min-h-(--list-row-h) w-full cursor-pointer items-center justify-between gap-3 border-0 bg-transparent px-0 py-3 text-left font-sans text-(length:--text-body) text-ink" @click="openDetail(household)">
            <span class="min-w-0">
              <span :class="[NAME, 'block']">{{ household.name }}</span>
              <span v-if="household.city" class="block text-sm text-muted [overflow-wrap:anywhere]">{{ household.city }}</span>
            </span>
            <span class="shrink-0 text-sm text-muted tabular-nums">{{ memberCountText(household.memberCount) }}</span>
          </button>
        </li>
      </ul>
    </template>

    <!-- Detail -->
    <BaseModal v-model="detailOpen" :title="selected?.name || 'Household'" size="md">
      <AlertBanner v-if="detailError">{{ detailError }}</AlertBanner>
      <p v-if="detailLoading" class="m-0 text-muted" role="status">Loading household...</p>
      <div v-else-if="detail" class="flex flex-col gap-5">
        <div>
          <h3 :class="SUBHEAD">Address</h3>
          <address v-if="addressLines.length" class="m-0 text-base not-italic [overflow-wrap:anywhere]">
            <div v-for="line in addressLines" :key="line">{{ line }}</div>
          </address>
          <p v-else class="m-0 text-base text-muted">No address recorded.</p>
        </div>
        <div v-if="detail.notes">
          <h3 :class="SUBHEAD">Notes</h3>
          <p class="m-0 text-base whitespace-pre-line [overflow-wrap:anywhere]">{{ detail.notes }}</p>
        </div>
        <div>
          <h3 :class="SUBHEAD">Members ({{ detail.members.length }})</h3>
          <ul v-if="detail.members.length" class="m-0 list-none border-t border-rule p-0">
            <li v-for="member in detail.members" :key="member.id" class="flex min-h-11 flex-wrap items-center justify-between gap-x-4 border-b border-rule py-2">
              <router-link :to="{ path: '/members', query: { search: member.name } }" class="font-medium [overflow-wrap:anywhere]">{{ member.name }}</router-link>
              <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
            </li>
          </ul>
          <p v-else class="m-0 text-base text-muted">
            No members yet.<template v-if="authStore.isStaff"> Choose this household when you add or edit a member.</template>
          </p>
        </div>
      </div>
      <template #footer>
        <BaseButton variant="secondary" @click="detailOpen = false">Close</BaseButton>
        <BaseButton v-if="authStore.isAdmin" variant="danger" :disabled="!detail" @click="showDeleteConfirm">Delete household</BaseButton>
        <BaseButton v-if="authStore.isStaff" :disabled="!detail" @click="showEditModal">Edit household</BaseButton>
      </template>
    </BaseModal>

    <!-- Add and edit -->
    <BaseModal v-model="formOpen" :title="editing ? 'Edit household' : 'Add household'" size="md">
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <form id="household-form" class="flex flex-col gap-4" novalidate @submit.prevent="saveHousehold">
        <BaseInput id="household-name" v-model="form.name" label="Name" autocomplete="off" hint="For example, Kebede family." :error="formErrors.name" />
        <BaseInput id="household-address1" v-model="form.addressLine1" label="Address" autocomplete="off" hint="Optional." :error="formErrors.addressLine1" />
        <BaseInput id="household-address2" v-model="form.addressLine2" label="Address line 2" autocomplete="off" :error="formErrors.addressLine2" />
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <BaseInput id="household-city" v-model="form.city" label="City" autocomplete="off" :error="formErrors.city" />
          <BaseInput id="household-postal" v-model="form.postalCode" label="Postal code" autocomplete="off" :error="formErrors.postalCode" />
        </div>
        <BaseTextarea id="household-notes" v-model="form.notes" label="Notes" :rows="3" :max="2000" hint="Optional. For the whole household, such as the best time to call." :error="formErrors.notes" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="saving" @click="formOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="household-form" :disabled="saving" :aria-busy="saving ? 'true' : undefined">
          {{ saving ? 'Saving...' : editing ? 'Save changes' : 'Add household' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Delete (ADMIN only) -->
    <ConfirmDialog
      v-model="deleteOpen"
      :title="`Delete ${selected?.name || 'household'}?`"
      message="This removes the household and its address and notes. Its members are not affected, and this cannot be undone."
      confirm-label="Delete household"
      danger
      :busy="deleting"
      @confirm="deleteHousehold"
    />
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { statusLabel, statusTone } from '@/utils/memberStatus'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import PageHead from '@/components/PageHead.vue'
import StatusLabel from '@/components/StatusLabel.vue'

import { CONTROL, LABEL, NAME, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'
const OPEN = 'cursor-pointer border-0 bg-transparent p-0 text-left font-sans text-(length:--text-body) font-medium text-teal underline underline-offset-2 hover:text-teal-hover [overflow-wrap:anywhere]'
const SUBHEAD = 'm-0 mb-1 font-sans text-[0.9375rem] font-medium text-muted'

const EMPTY_FORM = { name: '', addressLine1: '', addressLine2: '', city: '', postalCode: '', notes: '' }
const FIELDS = Object.keys(EMPTY_FORM)
const EMPTY_ERRORS = { ...EMPTY_FORM }

export default {
  name: 'HouseholdsView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseTextarea, ConfirmDialog, EmptyNote, Icon, PageHead, StatusLabel },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore(),
      TABLE, LABEL, CONTROL, TH, TD, NAME, OPEN, SUBHEAD,
      statusLabel,
      statusTone
    }
  },
  data() {
    return {
      households: [],
      loaded: false,
      loadError: false,
      search: '',
      selected: null,
      detail: null,
      detailOpen: false,
      detailLoading: false,
      detailError: '',
      form: { ...EMPTY_FORM },
      formOpen: false,
      editing: false,
      saving: false,
      formError: '',
      formErrors: { ...EMPTY_ERRORS },
      deleteOpen: false,
      deleting: false
    }
  },
  computed: {
    visibleHouseholds() {
      const term = this.search.trim().toLowerCase()
      return term ? this.households.filter(household => (household.name || '').toLowerCase().includes(term)) : this.households
    },
    addressLines() {
      const d = this.detail
      if (!d) return []
      const place = [d.postalCode, d.city].filter(Boolean).join(' ')
      return [d.addressLine1, d.addressLine2, place].filter(Boolean)
    }
  },
  async created() {
    await this.loadHouseholds()
  },
  methods: {
    async loadHouseholds() {
      try {
        const data = await api.getHouseholds()
        this.households = Array.isArray(data) ? data : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading households:', error)
        this.households = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    memberCountText(count) {
      return `${count} ${count === 1 ? 'member' : 'members'}`
    },
    async openDetail(household) {
      this.selected = household
      this.detail = null
      this.detailError = ''
      this.detailLoading = true
      this.detailOpen = true
      try {
        this.detail = await api.getHousehold(household.id)
      } catch (error) {
        console.error('Error loading household:', error)
        this.detailError = error.response?.status === 404
          ? 'This household no longer exists. Close this window to see the current list.'
          : 'The household did not load. Close this window and try again.'
      } finally {
        this.detailLoading = false
      }
    },
    resetFormErrors() {
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
    },
    showAddModal() {
      this.editing = false
      this.form = { ...EMPTY_FORM }
      this.resetFormErrors()
      this.formOpen = true
    },
    showEditModal() {
      this.editing = true
      this.form = Object.fromEntries(FIELDS.map(field => [field, this.detail[field] || '']))
      this.resetFormErrors()
      this.detailOpen = false
      this.formOpen = true
    },
    validateForm() {
      this.formErrors = { ...EMPTY_ERRORS }
      if (!this.form.name.trim()) this.formErrors.name = 'Enter the household\'s name.'
      else if (this.form.name.trim().length > 100) this.formErrors.name = 'Use 100 characters or fewer.'
      if (this.form.notes.length > 2000) this.formErrors.notes = 'Use 2000 characters or fewer.'
      return !this.formErrors.name && !this.formErrors.notes
    },
    // Put each server field error under its field; anything else goes in the banner
    showSaveError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        if (field in EMPTY_ERRORS && !this.formErrors[field]) this.formErrors[field] = message
        else rest.push(message)
      }
      if (!fieldErrors.length) rest.push(error.message || 'Request failed')
      if (rest.length) {
        this.formError = rest.join(' ')
        this.notifyFailure('Could not save household', error)
      }
    },
    async saveHousehold() {
      this.resetFormErrors()
      if (!this.validateForm()) return
      this.saving = true
      try {
        const request = Object.fromEntries(FIELDS.map(field => [field, this.form[field].trim()]))
        if (this.editing) await api.updateHousehold(this.selected.id, request)
        else await api.createHousehold(request)
        await this.loadHouseholds()
        this.formOpen = false
        this.notify('success', this.editing ? 'Household saved' : 'Household added', request.name)
      } catch (error) {
        console.error('Error saving household:', error)
        this.showSaveError(error)
      } finally {
        this.saving = false
      }
    },
    showDeleteConfirm() {
      this.deleteOpen = true
    },
    async deleteHousehold() {
      this.deleting = true
      try {
        const { id, name } = this.selected
        await api.deleteHousehold(id)
        await this.loadHouseholds()
        this.deleteOpen = false
        this.detailOpen = false
        this.notify('success', 'Household deleted', name)
      } catch (error) {
        console.error('Error deleting household:', error)
        // the server says why (for example, who is still assigned): show it in the open detail and in a toast
        this.deleteOpen = false
        this.detailError = error.message || 'Request failed'
        this.notifyFailure('Could not delete household', error)
      } finally {
        this.deleting = false
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || 'Request failed')
    }
  }
}
</script>

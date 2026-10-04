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
        <div>
          <h3 :class="SUBHEAD">People without a membership ({{ dependents.length }})</h3>
          <p class="m-0 mb-2 text-sm text-muted">Children, or a spouse who pays no dues. They get no messages and are not counted as members.</p>
          <ul v-if="dependents.length" class="m-0 list-none border-t border-rule p-0">
            <li v-for="person in dependents" :key="person.id" class="border-b border-rule py-2">
              <div class="min-h-6 font-medium [overflow-wrap:anywhere]">{{ person.name }}</div>
              <div v-if="person.birthDate" class="text-sm text-muted">Born {{ formatDay(person.birthDate) }}<template v-if="ageText(person.birthDate)"> &middot; {{ ageText(person.birthDate) }}</template></div>
              <div v-if="authStore.isStaff" class="-mx-2 mt-1 flex flex-wrap">
                <button type="button" :class="ROW_ACTION" @click="showEditPerson(person)">Edit<span class="sr-only"> {{ person.name }}</span></button>
                <button type="button" :class="ROW_ACTION" @click="showMembershipModal(person)">Make a member<span class="sr-only">: {{ person.name }}</span></button>
                <button v-if="authStore.isAdmin" type="button" :class="ROW_ACTION_DANGER" @click="showPersonDeleteConfirm(person)">Delete<span class="sr-only"> {{ person.name }}</span></button>
              </div>
            </li>
          </ul>
          <template v-else>
            <p class="m-0 text-base text-muted">
              Nobody here without a membership.<template v-if="authStore.isStaff"> Add a child or a spouse who pays no dues, so the household shows everyone.</template><template v-else> A staff member can add them.</template>
            </p>
          </template>
          <BaseButton v-if="authStore.isStaff" variant="secondary" class="mt-3 max-sm:min-h-11" @click="showAddPerson">
            <Icon name="plus" :size="16" class="mr-1.5" />Add person
          </BaseButton>
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

    <!-- Add and edit a person (STAFF and up) -->
    <BaseModal v-model="personFormOpen" :title="personEditing ? 'Edit person' : 'Add person'" size="md">
      <AlertBanner v-if="personFormError">{{ personFormError }}</AlertBanner>
      <form id="person-form" class="flex flex-col gap-4" novalidate @submit.prevent="savePerson">
        <p class="m-0 text-sm text-muted">Household: <span class="font-medium text-ink [overflow-wrap:anywhere]">{{ selected?.name }}</span></p>
        <BaseInput id="person-name" v-model="personForm.name" label="Name" autocomplete="off" :error="personErrors.name" />
        <BaseInput id="person-email" v-model="personForm.email" label="Email" type="email" autocomplete="off" hint="Optional." :error="personErrors.email" />
        <BaseInput id="person-phone" v-model="personForm.phone" label="Phone" type="tel" autocomplete="off" hint="Optional. 10 digits or more." :error="personErrors.phone" />
        <BaseInput id="person-birth" v-model="personForm.birthDate" label="Birth date" type="date" :max="yesterday" hint="Optional." :error="personErrors.birthDate" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="personSaving" @click="personFormOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="person-form" :disabled="personSaving" :aria-busy="personSaving ? 'true' : undefined">
          {{ personSaving ? 'Saving...' : personEditing ? 'Save changes' : 'Add person' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Make a member (STAFF and up) -->
    <BaseModal v-model="membershipOpen" :title="`Make ${personTarget?.name || 'this person'} a member`" size="sm">
      <AlertBanner v-if="membershipError">{{ membershipError }}</AlertBanner>
      <form id="membership-form" class="flex flex-col gap-4" novalidate @submit.prevent="startMembership">
        <BaseSelect id="membership-status" v-model="membershipForm.status" label="Status" hint="Only a Member owes dues and gets messages. Choose Inactive to keep them off both." :error="membershipErrors.status">
          <option v-for="option in NEW_MEMBER_STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </BaseSelect>
        <BaseInput id="membership-joined" v-model="membershipForm.joinDate" label="Joined on" type="date" :max="today" :error="membershipErrors.joinDate" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="membershipSaving" @click="membershipOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="membership-form" :disabled="membershipSaving" :aria-busy="membershipSaving ? 'true' : undefined">
          {{ membershipSaving ? 'Saving...' : 'Make a member' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Delete a person (ADMIN only) -->
    <ConfirmDialog
      v-model="personDeleteOpen"
      :title="`Delete ${personTarget?.name || 'this person'}?`"
      message="This removes them from the register and from this household. This cannot be undone."
      confirm-label="Delete person"
      danger
      :busy="personDeleting"
      @confirm="deletePerson"
    />
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { formatDate, isValidEmail, localISODate } from '@/utils'
import { NEW_MEMBER_STATUS_OPTIONS, statusLabel, statusTone } from '@/utils/memberStatus'
import { buildMembershipRequest, buildPersonRequest, ageText } from '@/utils/person'
import { isValidPhone } from '@/utils/phoneRules'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import PageHead from '@/components/PageHead.vue'
import StatusLabel from '@/components/StatusLabel.vue'

import { CONTROL, LABEL, NAME, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'
const OPEN = 'cursor-pointer border-0 bg-transparent p-0 text-left font-sans text-(length:--text-body) font-medium text-teal underline underline-offset-2 hover:text-teal-hover [overflow-wrap:anywhere]'
const SUBHEAD = 'm-0 mb-1 font-sans text-[0.9375rem] font-medium text-muted'
// a text action in a person's row: 44px tall, so it is easy to hit on a phone
const ROW_ACTION_BASE = 'inline-flex min-h-11 cursor-pointer items-center border-0 bg-transparent px-2 font-sans text-sm font-medium underline underline-offset-[3px]'
const ROW_ACTION = `${ROW_ACTION_BASE} text-teal hover:text-teal-hover`
const ROW_ACTION_DANGER = `${ROW_ACTION_BASE} text-clay hover:text-clay-hover`

const EMPTY_FORM = { name: '', addressLine1: '', addressLine2: '', city: '', postalCode: '', notes: '' }
const FIELDS = Object.keys(EMPTY_FORM)
const EMPTY_ERRORS = { ...EMPTY_FORM }

const EMPTY_PERSON = { name: '', email: '', phone: '', birthDate: '' }
const EMPTY_PERSON_ERRORS = { ...EMPTY_PERSON }
const EMPTY_MEMBERSHIP_ERRORS = { status: '', joinDate: '' }

export default {
  name: 'HouseholdsView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, ConfirmDialog, EmptyNote, Icon, PageHead, StatusLabel },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore(),
      TABLE, LABEL, CONTROL, TH, TD, NAME, OPEN, SUBHEAD, ROW_ACTION, ROW_ACTION_DANGER, NEW_MEMBER_STATUS_OPTIONS,
      ageText,
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
      deleting: false,
      personTarget: null,
      personForm: { ...EMPTY_PERSON },
      personFormOpen: false,
      personEditing: false,
      personSaving: false,
      personFormError: '',
      personErrors: { ...EMPTY_PERSON_ERRORS },
      membershipForm: { status: 'MEMBER', joinDate: '' },
      membershipOpen: false,
      membershipSaving: false,
      membershipError: '',
      membershipErrors: { ...EMPTY_MEMBERSHIP_ERRORS },
      personDeleteOpen: false,
      personDeleting: false,
      today: localISODate(),
      yesterday: ''
    }
  },
  computed: {
    visibleHouseholds() {
      const term = this.search.trim().toLowerCase()
      return term ? this.households.filter(household => (household.name || '').toLowerCase().includes(term)) : this.households
    },
    // people with no membership (memberStatus is null; an archived member still has one)
    dependents() {
      return (this.detail?.people || []).filter(person => person.memberStatus == null)
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
    formatDay(date) {
      return formatDate(date, 'MMM d, yyyy')
    },
    // After a person changes: the counts in the list and the people in the open household
    async reloadDetail() {
      try {
        this.detail = await api.getHousehold(this.selected.id)
      } catch (error) {
        console.error('Error reloading household:', error)
        this.detailError = error.response?.status === 404
          ? 'This household no longer exists. Close this window to see the current list.'
          : 'The household did not refresh. Close this window and open it again.'
      }
    },
    async reloadAfterPersonChange() {
      await Promise.all([this.loadHouseholds(), this.reloadDetail()])
    },
    // The messages for the answers the user can act on; '' means "show the server's own text"
    personProblem(error, name) {
      const code = error.response?.data?.code
      if (code === 'PERSON_001') return `${name} is already a member.`
      if (code === 'PERSON_002') return `${name} has a membership, so they cannot be deleted here. Archive the membership from the Members screen first.`
      if (error.response?.status === 404) return `${name} is no longer on the register. The list has been refreshed.`
      return ''
    },
    resetPersonErrors() {
      this.personFormError = ''
      this.personErrors = { ...EMPTY_PERSON_ERRORS }
    },
    showAddPerson() {
      this.personEditing = false
      this.personTarget = null
      this.personForm = { ...EMPTY_PERSON }
      this.yesterday = this.dayBeforeToday()
      this.resetPersonErrors()
      this.personFormOpen = true
    },
    // The household detail lacks email and phone: read the whole person before the form opens
    async showEditPerson(person) {
      this.detailError = ''
      try {
        const full = await api.getPerson(person.id)
        this.personEditing = true
        this.personTarget = full
        this.personForm = { name: full.name || '', email: full.email || '', phone: full.phone || '', birthDate: full.birthDate || '' }
        this.yesterday = this.dayBeforeToday()
        this.resetPersonErrors()
        this.personFormOpen = true
      } catch (error) {
        console.error('Error loading person:', error)
        const problem = this.personProblem(error, person.name)
        this.notifyFailure('Could not open person', problem || error)
        if (problem) await this.reloadAfterPersonChange()
      }
    },
    dayBeforeToday() {
      const day = new Date()
      day.setDate(day.getDate() - 1)
      return localISODate(day)
    },
    validatePerson() {
      const errors = { ...EMPTY_PERSON_ERRORS }
      const form = this.personForm
      if (!form.name.trim()) errors.name = 'Enter the person\'s name.'
      else if (form.name.trim().length > 100) errors.name = 'Use 100 characters or fewer.'
      const email = form.email.trim()
      if (email && !isValidEmail(email)) errors.email = 'Enter a valid email address, like name@example.com.'
      else if (email.length > 100) errors.email = 'Use 100 characters or fewer.'
      if (!isValidPhone(form.phone)) errors.phone = 'Enter a phone number with 10 digits or more.'
      if (form.birthDate && form.birthDate >= localISODate()) errors.birthDate = 'The birth date must be in the past.'
      this.personErrors = errors
      return !Object.values(errors).some(Boolean)
    },
    // Each server field error goes under its field; anything else (an unknown household, for one) goes in the banner
    showPersonSaveError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        if (field in EMPTY_PERSON_ERRORS && !this.personErrors[field]) this.personErrors[field] = message
        else rest.push(message)
      }
      if (!fieldErrors.length) rest.push(error.message || 'Request failed')
      if (rest.length) {
        this.personFormError = rest.join(' ')
        this.notifyFailure('Could not save person', error)
      }
    },
    async savePerson() {
      this.resetPersonErrors()
      if (!this.validatePerson()) return
      this.personSaving = true
      const editing = this.personEditing
      try {
        const request = buildPersonRequest(this.personForm, editing ? null : this.selected.id)
        if (editing) await api.updatePerson(this.personTarget.id, request)
        else await api.createPerson(request)
        await this.reloadAfterPersonChange()
        this.personFormOpen = false
        this.notify('success', editing ? 'Person saved' : 'Person added', request.name)
      } catch (error) {
        console.error('Error saving person:', error)
        const problem = editing ? this.personProblem(error, this.personTarget.name) : ''
        if (problem) {
          this.personFormOpen = false
          this.notifyFailure('Could not save person', problem)
          await this.reloadAfterPersonChange()
        } else {
          this.showPersonSaveError(error)
        }
      } finally {
        this.personSaving = false
      }
    },
    showMembershipModal(person) {
      this.detailError = ''
      this.personTarget = person
      this.today = localISODate()
      this.membershipForm = { status: 'MEMBER', joinDate: this.today }
      this.membershipError = ''
      this.membershipErrors = { ...EMPTY_MEMBERSHIP_ERRORS }
      this.membershipOpen = true
    },
    async startMembership() {
      this.membershipError = ''
      this.membershipErrors = { ...EMPTY_MEMBERSHIP_ERRORS }
      if (this.membershipForm.joinDate > localISODate()) {
        this.membershipErrors.joinDate = 'The join date cannot be in the future.'
        return
      }
      this.membershipSaving = true
      const { id, name } = this.personTarget
      try {
        await api.startMembership(id, buildMembershipRequest(this.membershipForm))
        // the person leaves this list now and appears in Members on its next load
        await this.reloadAfterPersonChange()
        this.membershipOpen = false
        this.notify('success', 'Made a member', name)
      } catch (error) {
        console.error('Error starting membership:', error)
        const problem = this.personProblem(error, name)
        if (problem) {
          this.membershipOpen = false
          this.notifyFailure('Could not make a member', problem)
          await this.reloadAfterPersonChange()
          return
        }
        const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
        const rest = []
        for (const { field, message } of fieldErrors) {
          if (field in EMPTY_MEMBERSHIP_ERRORS && !this.membershipErrors[field]) this.membershipErrors[field] = message
          else rest.push(message)
        }
        if (!fieldErrors.length) rest.push(error.message || 'Request failed')
        if (rest.length) {
          this.membershipError = rest.join(' ')
          this.notifyFailure('Could not make a member', error)
        }
      } finally {
        this.membershipSaving = false
      }
    },
    showPersonDeleteConfirm(person) {
      this.detailError = ''
      this.personTarget = person
      this.personDeleteOpen = true
    },
    async deletePerson() {
      this.personDeleting = true
      const { id, name } = this.personTarget
      try {
        await api.deletePerson(id)
        await this.reloadAfterPersonChange()
        this.personDeleteOpen = false
        this.notify('success', 'Person deleted', name)
      } catch (error) {
        console.error('Error deleting person:', error)
        // a person who became a member meanwhile (409), or one already gone (404): say so and refresh the list
        const problem = this.personProblem(error, name)
        this.personDeleteOpen = false
        this.detailError = problem || error.message || 'Request failed'
        this.notifyFailure('Could not delete person', problem || error)
        if (problem) await this.reloadAfterPersonChange()
      } finally {
        this.personDeleting = false
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, typeof error === 'string' ? error : error.message || 'Request failed')
    }
  }
}
</script>

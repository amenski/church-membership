<template>
  <div>
    <PageHead title="Members" lead="Everyone on the register: who is paid up and who is behind.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportMembers">
          <i class="bi bi-download mr-2" aria-hidden="true"></i>Export CSV
        </BaseButton>
        <BaseButton @click="showAddModal">
          <i class="bi bi-plus-lg mr-2" aria-hidden="true"></i>Add member
        </BaseButton>
      </template>
    </PageHead>

    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The member list did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadMembers">Try again</BaseButton>
      </div>
    </AlertBanner>

    <!-- Filters: one compact row from md up; the date pair folds away on a phone -->
    <form v-if="members.length" class="mb-6 grid grid-cols-2 gap-3 md:flex md:flex-wrap md:items-end" role="search" aria-label="Filter members" @submit.prevent>
      <div class="col-span-2 md:col-span-1 md:min-w-60 md:flex-1">
        <label for="filter-search" :class="LABEL">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search name, email or phone" autocomplete="off" :class="CONTROL">
      </div>
      <div class="md:w-40">
        <label for="filter-status" :class="LABEL">Status</label>
        <select id="filter-status" v-model="filters.status" :class="CONTROL">
          <option value="ALL">All members</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <div class="md:w-40">
        <label for="filter-dues" :class="LABEL">Dues</label>
        <select id="filter-dues" v-model="filters.paymentStatus" :class="CONTROL">
          <option value="ALL">All</option>
          <option value="CURRENT">Paid up</option>
          <option value="OVERDUE">Behind</option>
        </select>
      </div>
      <button
        type="button"
        class="col-span-2 flex min-h-11 cursor-pointer items-center gap-2 border-0 bg-transparent p-0 text-left font-medium text-teal hover:text-teal-hover md:hidden"
        aria-controls="filter-dates"
        :aria-expanded="datesOpen ? 'true' : 'false'"
        @click="datesOpen = !datesOpen"
      >
        <i :class="['bi', datesOpen ? 'bi-chevron-up' : 'bi-chevron-down']" aria-hidden="true"></i>
        {{ datesOpen ? 'Fewer filters' : 'More filters' }}<template v-if="!datesOpen && dateFilterCount"> ({{ dateFilterCount }} set)</template>
      </button>
      <div id="filter-dates" :class="datesOpen ? 'contents' : 'hidden md:contents'">
        <div class="md:w-40">
          <label for="filter-from" :class="LABEL">Joined from</label>
          <input id="filter-from" v-model="filters.joinedFrom" type="date" :class="CONTROL">
        </div>
        <div class="md:w-40">
          <label for="filter-to" :class="LABEL">Joined to</label>
          <input id="filter-to" v-model="filters.joinedTo" type="date" :class="CONTROL">
        </div>
      </div>
      <TextButton v-if="hasActiveFilters && filteredMembers.length" class="col-span-2 text-left md:col-span-1 md:py-1.5" @click="clearFilters">Clear filters</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !members.length">
      <EmptyNote>No members yet. Add the first member.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="showAddModal">Add member</BaseButton>
    </div>
    <div v-else-if="members.length && !filteredMembers.length">
      <EmptyNote>No members match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <template v-if="filteredMembers.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ hasActiveFilters ? `${filteredMembers.length} of ${members.length} members` : `${members.length} ${members.length === 1 ? 'member' : 'members'}` }}
      </p>

      <!-- md and up: ruled table -->
      <table class="hidden w-full border-collapse text-left text-(length:--text-body) tabular-nums md:table">
        <caption class="sr-only">Members</caption>
        <thead>
          <tr class="border-b border-rule">
            <th v-for="column in columns" :key="column.label" scope="col" :aria-sort="ariaSort(column.sortKey)" :class="[TH, column.class]">
              <button v-if="column.sortKey" type="button" :class="SORT_BUTTON" @click="setSort(column.sortKey)">
                {{ column.label }}
                <i :class="sortIcon(column.sortKey)" aria-hidden="true"></i>
              </button>
              <template v-else>{{ column.label }}</template>
            </th>
            <th v-if="authStore.isStaff" scope="col" :class="[TH, 'w-14']"><span class="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in filteredMembers" :key="member.id" class="h-(--row-h) border-b border-rule">
            <td :class="[TD, 'max-w-0 w-[34%]']">
              <div :class="NAME">{{ member.name }}</div>
              <div class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">
              <template v-if="member.phone">{{ member.phone }}</template>
              <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">No phone</span></span>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">{{ formatMemberDate(member.joinDate) }}</td>
            <td :class="[TD, 'whitespace-nowrap']">
              <StatusLabel :tone="member.active ? 'paid' : 'inactive'">{{ member.active ? 'Active' : 'Inactive' }}</StatusLabel>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">
              <span :class="duesClass(member)">
                <template v-if="member.active">{{ duesText(member) }}</template>
                <template v-else><span aria-hidden="true">&ndash;</span><span class="sr-only">Not tracked while inactive</span></template>
              </span>
            </td>
            <td v-if="authStore.isStaff" :class="[TD, 'text-right']">
              <ActionMenu :label="`More actions for ${member.name}`" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Below md: the same rows, stacked -->
      <ul class="m-0 list-none border-t border-rule p-0 md:hidden">
        <li v-for="member in filteredMembers" :key="member.id" class="flex items-start justify-between gap-2 border-b border-rule py-3">
          <div class="min-w-0 flex-1">
            <div :class="NAME">{{ member.name }}</div>
            <div class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
            <div class="mt-1 flex flex-wrap items-center gap-x-4">
              <StatusLabel :tone="member.active ? 'paid' : 'inactive'">{{ member.active ? 'Active' : 'Inactive' }}</StatusLabel>
              <span v-if="member.active" :class="duesClass(member)">{{ duesText(member) }}</span>
            </div>
            <div class="mt-1 text-sm text-muted tabular-nums">
              <template v-if="member.phone">{{ member.phone }} &middot; </template>Joined {{ formatMemberDate(member.joinDate) }}
            </div>
          </div>
          <ActionMenu v-if="authStore.isStaff" :label="`More actions for ${member.name}`" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
        </li>
      </ul>
    </template>

    <!-- Add and edit -->
    <BaseModal v-model="formOpen" :title="editingMember ? 'Edit member' : 'Add member'" size="md">
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <form id="member-form" class="flex flex-col gap-4" novalidate @submit.prevent="saveMember">
        <BaseInput id="member-name" v-model="memberForm.name" label="Name" autocomplete="off" :error="formErrors.name" />
        <BaseInput id="member-email" v-model="memberForm.email" label="Email" type="email" autocomplete="off" :error="formErrors.email" />
        <BaseInput id="member-phone" v-model="memberForm.phone" label="Phone" type="tel" autocomplete="off" hint="Optional. 10 digits or more." :error="formErrors.phone" />
        <BaseInput id="member-joined" v-model="memberForm.joinDate" label="Joined on" type="date" :max="today" :error="formErrors.joinDate" />
        <div v-if="editingMember">
          <label class="flex min-h-11 cursor-pointer items-center gap-3">
            <input v-model="memberForm.active" type="checkbox" role="switch" class="peer sr-only">
            <span
              class="relative h-6 w-11 shrink-0 rounded-full border border-field bg-paper transition-colors after:absolute after:top-0.5 after:left-0.5 after:size-4 after:rounded-full after:bg-field after:transition-transform peer-checked:border-teal peer-checked:bg-teal peer-checked:after:translate-x-5 peer-checked:after:bg-paper peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-teal motion-reduce:transition-none motion-reduce:after:transition-none"
              aria-hidden="true"
            ></span>
            <span class="text-base font-medium text-ink">Active</span>
          </label>
          <p class="mt-1 mb-0 text-[0.9375rem] text-muted">Inactive members stay on the register but do not count as behind. Turning this back on resets the months behind.</p>
        </div>
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="saving" @click="formOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="member-form" :disabled="saving" :aria-busy="saving ? 'true' : undefined">
          {{ saving ? 'Saving...' : editingMember ? 'Save changes' : 'Add member' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Delete (ADMIN only) -->
    <BaseModal v-model="deleteOpen" :title="`Delete ${selectedMember?.name || 'member'}?`" size="sm">
      <AlertBanner v-if="deleteError">{{ deleteError }}</AlertBanner>
      <p class="m-0 text-base">This permanently deletes the member together with their payments and message history. This cannot be undone.</p>
      <template #footer>
        <BaseButton variant="secondary" :disabled="deleting" @click="deleteOpen = false">Cancel</BaseButton>
        <BaseButton variant="danger" :disabled="deleting" :aria-busy="deleting ? 'true' : undefined" @click="deleteMember">
          {{ deleting ? 'Deleting...' : 'Delete member' }}
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, isValidEmail, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dashboardMeter'
import { filterMembers, sortMembers, exportIds } from '@/utils/memberFilters'
import { buildMemberRequest } from '@/utils/memberPayload'
import ActionMenu from '@/components/ActionMenu.vue'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'

const LABEL = 'mb-1 block text-(length:--text-label) leading-(--lh-label) font-medium text-muted'
const CONTROL = 'block h-(--control-h) w-full rounded-md border border-field bg-paper px-3 text-(length:--text-body) text-ink placeholder:text-muted placeholder:opacity-80 focus:border-teal focus:outline-2 focus:outline-offset-1 focus:outline-teal'
const TH = 'px-3 py-2 text-[0.9375rem] font-medium text-muted first:pl-0 last:pr-0'
const TD = 'px-3 py-2 align-middle first:pl-0 last:pr-0'
const SORT_BUTTON = '-mx-1 inline-flex cursor-pointer items-center gap-1 rounded-md border-0 bg-transparent px-1 py-1 font-sans text-[0.9375rem] font-medium text-muted hover:text-ink'
const NAME = 'font-sans font-medium text-ink [overflow-wrap:anywhere]'

const EMPTY_FILTERS = { search: '', status: 'ALL', paymentStatus: 'ALL', joinedFrom: '', joinedTo: '' }
const EMPTY_ERRORS = { name: '', email: '', phone: '', joinDate: '' }

export default {
  name: 'MembersView',
  components: { ActionMenu, AlertBanner, BaseButton, BaseInput, BaseModal, EmptyNote, PageHead, StatusLabel, TextButton },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore()
    }
  },
  data() {
    return {
      members: [],
      loaded: false,
      loadError: false,
      filters: { ...EMPTY_FILTERS },
      datesOpen: false,
      sort: { key: null, direction: 'asc' },
      memberForm: { name: '', email: '', phone: '', joinDate: '', active: true },
      formOpen: false,
      saving: false,
      formError: '',
      formErrors: { ...EMPTY_ERRORS },
      editingMember: null,
      selectedMember: null,
      deleteOpen: false,
      deleting: false,
      deleteError: '',
      today: localISODate(),
      LABEL,
      CONTROL,
      TH,
      TD,
      SORT_BUTTON,
      NAME
    }
  },
  computed: {
    filteredMembers() {
      const filtered = filterMembers(this.members, this.filters)
      return this.sort.key ? sortMembers(filtered, this.sort.key, this.sort.direction) : filtered
    },
    hasActiveFilters() {
      const f = this.filters
      return !!f.search.trim() || f.status !== 'ALL' || f.paymentStatus !== 'ALL' || !!f.joinedFrom || !!f.joinedTo
    },
    dateFilterCount() {
      return (this.filters.joinedFrom ? 1 : 0) + (this.filters.joinedTo ? 1 : 0)
    },
    columns() {
      return [
        { label: 'Name', sortKey: 'name' },
        { label: 'Phone' },
        { label: 'Joined', sortKey: 'joinDate' },
        { label: 'Status' },
        { label: 'Dues', sortKey: 'consecutiveMonthsMissed' }
      ]
    }
  },
  async created() {
    await this.loadMembers()
  },
  methods: {
    async loadMembers() {
      try {
        const data = await api.getMembers()
        // Ensure members is always an array
        this.members = Array.isArray(data) ? data : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading members:', error)
        this.members = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    setSort(key) {
      if (this.sort.key === key) {
        this.sort.direction = this.sort.direction === 'asc' ? 'desc' : 'asc'
      } else {
        this.sort = { key, direction: 'asc' }
      }
    },
    ariaSort(key) {
      if (!key) return undefined
      if (this.sort.key !== key) return 'none'
      return this.sort.direction === 'asc' ? 'ascending' : 'descending'
    },
    sortIcon(key) {
      if (this.sort.key !== key) return 'bi bi-chevron-expand text-[0.75rem]'
      return `bi text-[0.75rem] text-ink ${this.sort.direction === 'asc' ? 'bi-caret-up-fill' : 'bi-caret-down-fill'}`
    },
    clearFilters() {
      this.filters = { ...EMPTY_FILTERS }
    },
    formatMemberDate(date) {
      return date ? formatDate(date, 'MMM d, yyyy') : ''
    },
    // Dues are tracked for active members only; an inactive member's stored figure is stale
    duesText(member) {
      return member.consecutiveMonthsMissed > 0 ? monthsBehind(member.consecutiveMonthsMissed) : 'Paid up'
    },
    duesClass(member) {
      if (!member.active) return 'text-muted'
      return ['font-medium', member.consecutiveMonthsMissed > 0 ? 'text-ochre-text' : 'text-fern-text']
    },
    menuItems(member) {
      const items = [
        { key: 'edit', label: 'Edit' },
        { key: 'toggle', label: member.active ? 'Deactivate' : 'Reactivate' }
      ]
      if (this.authStore.isAdmin) items.push({ key: 'delete', label: 'Delete', danger: true })
      return items
    },
    onMenuSelect(key, member) {
      if (key === 'edit') this.showEditModal(member)
      else if (key === 'toggle') this.toggleStatus(member)
      else if (key === 'delete') this.showDeleteModal(member)
    },
    resetFormErrors() {
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
    },
    showAddModal() {
      this.editingMember = null
      this.memberForm = { name: '', email: '', phone: '', joinDate: localISODate(), active: true }
      this.today = localISODate()
      this.resetFormErrors()
      this.formOpen = true
    },
    showEditModal(member) {
      this.editingMember = member
      this.memberForm = {
        name: member.name || '',
        email: member.email || '',
        phone: member.phone || '',
        joinDate: member.joinDate || '',
        active: !!member.active
      }
      this.today = localISODate()
      this.resetFormErrors()
      this.formOpen = true
    },
    showDeleteModal(member) {
      this.selectedMember = member
      this.deleteError = ''
      this.deleteOpen = true
    },
    validateForm() {
      this.formErrors = { ...EMPTY_ERRORS }
      if (!this.memberForm.name.trim()) this.formErrors.name = 'Enter the member\'s name.'
      const email = this.memberForm.email.trim()
      if (!email) this.formErrors.email = 'Enter an email address.'
      else if (!isValidEmail(email)) this.formErrors.email = 'Enter a valid email address, like name@example.com.'
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
      const message = error.message || 'Request failed'
      if (!fieldErrors.length) {
        // A duplicate email arrives as a plain 400 detail, with no field list
        if (/email/i.test(message) && /already exists/i.test(message)) this.formErrors.email = message
        else rest.push(message)
      }
      if (rest.length) {
        this.formError = rest.join(' ')
        this.notifyFailure('Could not save member', error)
      }
    },
    async saveMember() {
      this.resetFormErrors()
      if (!this.validateForm()) return
      this.saving = true
      try {
        const request = buildMemberRequest(this.memberForm)
        const editing = !!this.editingMember
        if (editing) {
          await api.updateMember(this.editingMember.id, request)
        } else {
          await api.createMember(request)
        }
        await this.loadMembers()
        this.formOpen = false
        this.notify('success', editing ? 'Member saved' : 'Member added', request.name)
      } catch (error) {
        console.error('Error saving member:', error)
        this.showSaveError(error)
      } finally {
        this.saving = false
      }
    },
    async deleteMember() {
      this.deleting = true
      this.deleteError = ''
      try {
        const { id, name } = this.selectedMember
        await api.deleteMember(id)
        await this.loadMembers()
        this.deleteOpen = false
        this.notify('success', 'Member deleted', name)
      } catch (error) {
        console.error('Error deleting member:', error)
        this.deleteError = error.message || 'Request failed'
        this.notifyFailure('Could not delete member', error)
      } finally {
        this.deleting = false
      }
    },
    async toggleStatus(member) {
      const reactivating = !member.active
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, active: reactivating }))
        await this.loadMembers()
        this.notify('success', reactivating ? 'Member reactivated' : 'Member deactivated', member.name)
      } catch (error) {
        console.error('Error toggling member status:', error)
        this.notifyFailure(reactivating ? 'Could not reactivate member' : 'Could not deactivate member', error)
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || 'Request failed')
    },
    async exportMembers() {
      if (this.filteredMembers.length === 0) {
        this.appStore.addNotification({
          type: 'warning',
          title: 'Nothing to export',
          message: 'No members match the current filters',
          isToast: true
        })
        return
      }
      try {
        const ids = exportIds(this.filteredMembers, this.members)
        const response = await api.exportMembers(ids)
        // api.request() already returns response.data (the blob)
        const prefix = this.hasActiveFilters ? 'members_filtered' : 'members'
        downloadBlob(response, `${prefix}_${new Date().toISOString().split('T')[0]}.csv`)
      } catch (error) {
        console.error('Error exporting members:', error)
        this.appStore.addNotification({
          type: 'error',
          title: 'Export failed',
          message: error.message || 'Could not export CSV',
          isToast: true
        })
      }
    }
  }
}
</script>

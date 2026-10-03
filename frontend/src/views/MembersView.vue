<template>
  <div>
    <PageHead title="Members" lead="Everyone on the register: who is paid up and who is behind.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportMembers">
          <i class="bi bi-download tw:mr-2" aria-hidden="true"></i>Export CSV
        </BaseButton>
        <BaseButton @click="showAddModal">
          <i class="bi bi-plus-lg tw:mr-2" aria-hidden="true"></i>Add member
        </BaseButton>
      </template>
    </PageHead>

    <AlertBanner v-if="loadError">
      <div class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
        <span>The member list did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadMembers">Try again</BaseButton>
      </div>
    </AlertBanner>

    <!-- Filters: one compact row from md up; the date pair folds away on a phone -->
    <form v-if="members.length" class="tw:mb-6 tw:grid tw:grid-cols-2 tw:gap-3 tw:md:flex tw:md:flex-wrap tw:md:items-end" role="search" aria-label="Filter members" @submit.prevent>
      <div class="tw:col-span-2 tw:md:col-span-1 tw:md:min-w-60 tw:md:flex-1">
        <label for="filter-search" :class="LABEL">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search name, email or phone" autocomplete="off" :class="CONTROL">
      </div>
      <div class="tw:md:w-40">
        <label for="filter-status" :class="LABEL">Status</label>
        <select id="filter-status" v-model="filters.status" :class="CONTROL">
          <option value="ALL">All members</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>
      <div class="tw:md:w-40">
        <label for="filter-dues" :class="LABEL">Dues</label>
        <select id="filter-dues" v-model="filters.paymentStatus" :class="CONTROL">
          <option value="ALL">All</option>
          <option value="CURRENT">Paid up</option>
          <option value="OVERDUE">Behind</option>
        </select>
      </div>
      <button
        type="button"
        class="tw:col-span-2 tw:flex tw:min-h-11 tw:cursor-pointer tw:items-center tw:gap-2 tw:border-0 tw:bg-transparent tw:p-0 tw:text-left tw:font-medium tw:text-teal tw:hover:text-teal-hover tw:md:hidden"
        aria-controls="filter-dates"
        :aria-expanded="datesOpen ? 'true' : 'false'"
        @click="datesOpen = !datesOpen"
      >
        <i :class="['bi', datesOpen ? 'bi-chevron-up' : 'bi-chevron-down']" aria-hidden="true"></i>
        {{ datesOpen ? 'Fewer filters' : 'More filters' }}<template v-if="!datesOpen && dateFilterCount"> ({{ dateFilterCount }} set)</template>
      </button>
      <div id="filter-dates" :class="datesOpen ? 'tw:contents' : 'tw:hidden tw:md:contents'">
        <div class="tw:md:w-40">
          <label for="filter-from" :class="LABEL">Joined from</label>
          <input id="filter-from" v-model="filters.joinedFrom" type="date" :class="CONTROL">
        </div>
        <div class="tw:md:w-40">
          <label for="filter-to" :class="LABEL">Joined to</label>
          <input id="filter-to" v-model="filters.joinedTo" type="date" :class="CONTROL">
        </div>
      </div>
      <TextButton v-if="hasActiveFilters && filteredMembers.length" class="tw:col-span-2 tw:text-left tw:md:col-span-1 tw:md:py-1.5" @click="clearFilters">Clear filters</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !members.length">
      <EmptyNote>No members yet. Add the first member.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="tw:mt-2" @click="showAddModal">Add member</BaseButton>
    </div>
    <div v-else-if="members.length && !filteredMembers.length">
      <EmptyNote>No members match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="tw:mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <template v-if="filteredMembers.length">
      <p class="tw:mt-0 tw:mb-2 tw:text-sm tw:text-muted" aria-live="polite">
        {{ hasActiveFilters ? `${filteredMembers.length} of ${members.length} members` : `${members.length} ${members.length === 1 ? 'member' : 'members'}` }}
      </p>

      <!-- md and up: ruled table -->
      <table class="tw:hidden tw:w-full tw:border-collapse tw:text-left tw:text-(length:--text-body) tw:tabular-nums tw:md:table">
        <caption class="tw:sr-only">Members</caption>
        <thead>
          <tr class="tw:border-b tw:border-rule">
            <th v-for="column in columns" :key="column.label" scope="col" :aria-sort="ariaSort(column.sortKey)" :class="[TH, column.class]">
              <button v-if="column.sortKey" type="button" :class="SORT_BUTTON" @click="setSort(column.sortKey)">
                {{ column.label }}
                <i :class="sortIcon(column.sortKey)" aria-hidden="true"></i>
              </button>
              <template v-else>{{ column.label }}</template>
            </th>
            <th v-if="authStore.isStaff" scope="col" :class="[TH, 'tw:w-14']"><span class="tw:sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in filteredMembers" :key="member.id" class="tw:h-(--row-h) tw:border-b tw:border-rule">
            <td :class="[TD, 'tw:max-w-0 tw:w-[34%]']">
              <div :class="NAME">{{ member.name }}</div>
              <div class="tw:text-sm tw:text-muted tw:[overflow-wrap:anywhere]">{{ member.email }}</div>
            </td>
            <td :class="[TD, 'tw:whitespace-nowrap']">
              <template v-if="member.phone">{{ member.phone }}</template>
              <span v-else class="tw:text-muted"><span aria-hidden="true">&ndash;</span><span class="tw:sr-only">No phone</span></span>
            </td>
            <td :class="[TD, 'tw:whitespace-nowrap']">{{ formatMemberDate(member.joinDate) }}</td>
            <td :class="[TD, 'tw:whitespace-nowrap']">
              <StatusLabel :tone="member.active ? 'paid' : 'inactive'">{{ member.active ? 'Active' : 'Inactive' }}</StatusLabel>
            </td>
            <td :class="[TD, 'tw:whitespace-nowrap']">
              <span :class="duesClass(member)">
                <template v-if="member.active">{{ duesText(member) }}</template>
                <template v-else><span aria-hidden="true">&ndash;</span><span class="tw:sr-only">Not tracked while inactive</span></template>
              </span>
            </td>
            <td v-if="authStore.isStaff" :class="[TD, 'tw:text-right']">
              <ActionMenu :label="`More actions for ${member.name}`" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Below md: the same rows, stacked -->
      <ul class="tw:m-0 tw:list-none tw:border-t tw:border-rule tw:p-0 tw:md:hidden">
        <li v-for="member in filteredMembers" :key="member.id" class="tw:flex tw:items-start tw:justify-between tw:gap-2 tw:border-b tw:border-rule tw:py-3">
          <div class="tw:min-w-0 tw:flex-1">
            <div :class="NAME">{{ member.name }}</div>
            <div class="tw:text-sm tw:text-muted tw:[overflow-wrap:anywhere]">{{ member.email }}</div>
            <div class="tw:mt-1 tw:flex tw:flex-wrap tw:items-center tw:gap-x-4">
              <StatusLabel :tone="member.active ? 'paid' : 'inactive'">{{ member.active ? 'Active' : 'Inactive' }}</StatusLabel>
              <span v-if="member.active" :class="duesClass(member)">{{ duesText(member) }}</span>
            </div>
            <div class="tw:mt-1 tw:text-sm tw:text-muted tw:tabular-nums">
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
      <form id="member-form" class="tw:flex tw:flex-col tw:gap-4" novalidate @submit.prevent="saveMember">
        <BaseInput id="member-name" v-model="memberForm.name" label="Name" autocomplete="off" :error="formErrors.name" />
        <BaseInput id="member-email" v-model="memberForm.email" label="Email" type="email" autocomplete="off" :error="formErrors.email" />
        <BaseInput id="member-phone" v-model="memberForm.phone" label="Phone" type="tel" autocomplete="off" hint="Optional. 10 digits or more." :error="formErrors.phone" />
        <BaseInput id="member-joined" v-model="memberForm.joinDate" label="Joined on" type="date" :max="today" :error="formErrors.joinDate" />
        <div>
          <label class="tw:flex tw:min-h-11 tw:cursor-pointer tw:items-center tw:gap-3">
            <input v-model="memberForm.active" type="checkbox" role="switch" class="tw:peer tw:sr-only">
            <span
              class="tw:relative tw:h-6 tw:w-11 tw:shrink-0 tw:rounded-full tw:border tw:border-field tw:bg-paper tw:transition-colors tw:after:absolute tw:after:top-0.5 tw:after:left-0.5 tw:after:size-4 tw:after:rounded-full tw:after:bg-field tw:after:transition-transform tw:peer-checked:border-teal tw:peer-checked:bg-teal tw:peer-checked:after:translate-x-5 tw:peer-checked:after:bg-paper tw:peer-focus-visible:outline-2 tw:peer-focus-visible:outline-offset-2 tw:peer-focus-visible:outline-teal tw:motion-reduce:transition-none tw:motion-reduce:after:transition-none"
              aria-hidden="true"
            ></span>
            <span class="tw:text-base tw:font-medium tw:text-ink">Active</span>
          </label>
          <p class="tw:mt-1 tw:mb-0 tw:text-[0.9375rem] tw:text-muted">Inactive members stay on the register but do not count as behind. Turning this back on resets the months behind.</p>
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
      <p class="tw:m-0 tw:text-base">This permanently deletes the member together with their payments and message history. This cannot be undone.</p>
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

const LABEL = 'tw:mb-1 tw:block tw:text-(length:--text-label) tw:leading-(--lh-label) tw:font-medium tw:text-muted'
const CONTROL = 'tw:block tw:h-(--control-h) tw:w-full tw:rounded-md tw:border tw:border-field tw:bg-paper tw:px-3 tw:text-(length:--text-body) tw:text-ink tw:placeholder:text-muted tw:placeholder:opacity-80 tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal'
const TH = 'tw:px-3 tw:py-2 tw:text-[0.9375rem] tw:font-medium tw:text-muted tw:first:pl-0 tw:last:pr-0'
const TD = 'tw:px-3 tw:py-2 tw:align-middle tw:first:pl-0 tw:last:pr-0'
const SORT_BUTTON = 'tw:-mx-1 tw:inline-flex tw:cursor-pointer tw:items-center tw:gap-1 tw:rounded-md tw:border-0 tw:bg-transparent tw:px-1 tw:py-1 tw:font-sans tw:text-[0.9375rem] tw:font-medium tw:text-muted tw:hover:text-ink'
const NAME = 'tw:font-sans tw:font-medium tw:text-ink tw:[overflow-wrap:anywhere]'

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
      if (this.sort.key !== key) return 'bi bi-chevron-expand tw:text-[0.75rem]'
      return `bi tw:text-[0.75rem] tw:text-ink ${this.sort.direction === 'asc' ? 'bi-caret-up-fill' : 'bi-caret-down-fill'}`
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
      if (!member.active) return 'tw:text-muted'
      return ['tw:font-medium', member.consecutiveMonthsMissed > 0 ? 'tw:text-ochre-text' : 'tw:text-fern-text']
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

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
    <form v-if="members.length || showingArchived" class="mb-6 grid grid-cols-2 gap-3 md:flex md:flex-wrap md:items-end" role="search" aria-label="Filter members" @submit.prevent>
      <div class="col-span-2 md:col-span-1 md:min-w-60 md:flex-1">
        <label for="filter-search" :class="LABEL">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search name, email or phone" autocomplete="off" :class="CONTROL">
      </div>
      <div class="md:w-40">
        <label for="filter-status" :class="LABEL">Status</label>
        <select id="filter-status" v-model="filters.status" :class="CONTROL">
          <option value="ALL">All members</option>
          <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          <option v-if="authStore.isAdmin" value="ARCHIVED">Archived</option>
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
    <div v-if="showingArchived && !source.length">
      <p v-if="!archivedLoaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading archived members...</p>
      <EmptyNote v-else>No archived members.</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">Back to all members</BaseButton>
    </div>
    <div v-else-if="loaded && !loadError && !members.length">
      <EmptyNote>No members yet. Add the first member.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="showAddModal">Add member</BaseButton>
    </div>
    <div v-else-if="source.length && !filteredMembers.length">
      <EmptyNote>No members match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <template v-if="filteredMembers.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ filteredMembers.length !== source.length ? `${filteredMembers.length} of ${source.length} members` : `${source.length} ${source.length === 1 ? 'member' : 'members'}` }}
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
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="text-sm text-muted [overflow-wrap:anywhere]"><i class="bi bi-house mr-1" aria-hidden="true"></i><span class="sr-only">Household: </span>{{ member.householdName }}</div>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">
              <template v-if="member.phone">{{ member.phone }}</template>
              <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">No phone</span></span>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">{{ formatMemberDate(member.joinDate) }}</td>
            <td :class="[TD, 'whitespace-nowrap']">
              <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
            </td>
            <td :class="[TD, 'whitespace-nowrap']">
              <span :class="duesClass(member)">
                <template v-if="countsForDues(member)">{{ duesText(member) }}</template>
                <template v-else><span aria-hidden="true">&ndash;</span><span class="sr-only">Not tracked while {{ statusLabel(member.status).toLowerCase() }}</span></template>
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
            <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
            <div v-if="member.householdName" class="text-sm text-muted [overflow-wrap:anywhere]"><i class="bi bi-house mr-1" aria-hidden="true"></i><span class="sr-only">Household: </span>{{ member.householdName }}</div>
            <div class="mt-1 flex flex-wrap items-center gap-x-4">
              <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
              <span v-if="countsForDues(member)" :class="duesClass(member)">{{ duesText(member) }}</span>
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
        <BaseInput id="member-email" v-model="memberForm.email" label="Email" type="email" autocomplete="off" hint="Optional. Two members can share one address and get one message. Someone without an email gets no messages: for a child without one, choose Inactive below so they are not counted as owing dues." :error="formErrors.email" />
        <BaseInput id="member-phone" v-model="memberForm.phone" label="Phone" type="tel" autocomplete="off" hint="Optional. 10 digits or more." :error="formErrors.phone" />
        <BaseInput id="member-joined" v-model="memberForm.joinDate" label="Joined on" type="date" :max="today" :error="formErrors.joinDate" />
        <BaseSelect
          id="member-household"
          label="Household"
          :model-value="memberForm.householdId"
          :disabled="householdsFailed"
          :hint="householdsFailed ? 'The household list did not load. Close this window and try again to change the household.' : 'Optional. Members of one family or address share a household.'"
          :error="formErrors.householdId"
          @update:model-value="chooseHousehold"
        >
          <option value="">None</option>
          <option v-for="household in householdOptions" :key="household.id" :value="String(household.id)">{{ household.name }}</option>
        </BaseSelect>
        <BaseSelect id="member-status" v-model="memberForm.status" label="Status" :hint="statusHint">
          <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </BaseSelect>
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="saving" @click="formOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="member-form" :disabled="saving" :aria-busy="saving ? 'true' : undefined">
          {{ saving ? 'Saving...' : editingMember ? 'Save changes' : 'Add member' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Archive (ADMIN only) -->
    <BaseModal v-model="deleteOpen" :title="`Archive ${selectedMember?.name || 'member'}?`" size="sm">
      <AlertBanner v-if="deleteError">{{ deleteError }}</AlertBanner>
      <p class="m-0 text-base">This hides {{ selectedMember?.name || 'the member' }} from the lists. Their payments and messages are kept.</p>
      <template #footer>
        <BaseButton variant="secondary" :disabled="deleting" @click="deleteOpen = false">Cancel</BaseButton>
        <BaseButton variant="danger" :disabled="deleting" :aria-busy="deleting ? 'true' : undefined" @click="deleteMember">
          {{ deleting ? 'Archiving...' : 'Archive member' }}
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
import { NEW_MEMBER_STATUS_OPTIONS, STATUS_OPTIONS, countsForDues, isArchived, statusLabel, statusTone } from '@/utils/memberStatus'
import ActionMenu from '@/components/ActionMenu.vue'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
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
const EMPTY_ERRORS = { name: '', email: '', phone: '', joinDate: '', householdId: '' }

export default {
  name: 'MembersView',
  components: { ActionMenu, AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, EmptyNote, PageHead, StatusLabel, TextButton },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore()
    }
  },
  data() {
    return {
      members: [],
      archivedMembers: [],
      archivedLoaded: false,
      loaded: false,
      loadError: false,
      filters: { ...EMPTY_FILTERS },
      datesOpen: false,
      sort: { key: null, direction: 'asc' },
      memberForm: { name: '', email: '', phone: '', joinDate: '', status: 'MEMBER', householdId: '', householdTouched: false },
      households: [],
      householdsFailed: false,
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
      NAME,
      STATUS_OPTIONS,
      countsForDues,
      statusLabel,
      statusTone
    }
  },
  computed: {
    showingArchived() {
      return this.filters.status === 'ARCHIVED' && this.authStore.isAdmin
    },
    // the list on screen: the archived list (ADMIN, loaded on demand) or the normal one
    source() {
      return this.showingArchived ? this.archivedMembers : this.members
    },
    statusOptions() {
      return this.editingMember ? STATUS_OPTIONS : NEW_MEMBER_STATUS_OPTIONS
    },
    // the loaded households, plus the member's current one when the list lacks it (so the select never shows a blank)
    householdOptions() {
      const current = this.editingMember
      if (current?.householdId && !this.households.some(household => household.id === current.householdId)) {
        return [...this.households, { id: current.householdId, name: current.householdName || 'Current household' }]
      }
      return this.households
    },
    statusHint() {
      return this.editingMember
        ? 'Only a Member owes dues and gets messages. Moving someone back to Member resets the months behind.'
        : 'Choose Inactive for someone who should not owe dues or get messages, such as a child.'
    },
    filteredMembers() {
      const filtered = filterMembers(this.source, this.filters)
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
  watch: {
    'filters.status'(status) {
      if (status === 'ARCHIVED' && this.authStore.isAdmin) this.loadArchived()
    }
  },
  async created() {
    // /households links to a member's name: show that search
    const search = this.$route?.query?.search
    if (typeof search === 'string') this.filters.search = search
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
    async loadArchived() {
      try {
        const data = await api.getMembers({ archived: true })
        this.archivedMembers = Array.isArray(data) ? data : []
      } catch (error) {
        console.error('Error loading archived members:', error)
        this.archivedMembers = []
        this.notifyFailure('Could not load archived members', error)
      } finally {
        this.archivedLoaded = true
      }
    },
    // after a change: the normal list, and the archived one when it is on screen
    async reloadLists() {
      await this.loadMembers()
      if (this.showingArchived) await this.loadArchived()
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
    // Dues are tracked for members with status MEMBER only; for any other status the stored figure is stale
    duesText(member) {
      return member.consecutiveMonthsMissed > 0 ? monthsBehind(member.consecutiveMonthsMissed) : 'Paid up'
    },
    duesClass(member) {
      if (!countsForDues(member)) return 'text-muted'
      return ['font-medium', member.consecutiveMonthsMissed > 0 ? 'text-ochre-text' : 'text-fern-text']
    },
    menuItems(member) {
      // an archived member (only an ADMIN sees them) can only be restored
      if (isArchived(member)) return [{ key: 'restore', label: 'Restore' }]
      const items = [{ key: 'edit', label: 'Edit' }]
      if (member.status === 'MEMBER') items.push({ key: 'toggle', label: 'Mark inactive' })
      else if (member.status === 'INACTIVE') items.push({ key: 'toggle', label: 'Mark active' })
      items.push({ key: 'status', label: 'Change status...' })
      if (this.authStore.isAdmin) items.push({ key: 'delete', label: 'Archive', danger: true })
      return items
    },
    onMenuSelect(key, member) {
      if (key === 'edit') this.showEditModal(member)
      else if (key === 'status') this.showEditModal(member, true)
      else if (key === 'toggle') this.toggleStatus(member)
      else if (key === 'restore') this.restoreMember(member)
      else if (key === 'delete') this.showDeleteModal(member)
    },
    resetFormErrors() {
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
    },
    showAddModal() {
      this.editingMember = null
      this.memberForm = { name: '', email: '', phone: '', joinDate: localISODate(), status: 'MEMBER', householdId: '', householdTouched: false }
      this.today = localISODate()
      this.resetFormErrors()
      this.formOpen = true
      this.loadHouseholds()
    },
    showEditModal(member, focusStatus = false) {
      this.editingMember = member
      this.memberForm = {
        name: member.name || '',
        email: member.email || '',
        phone: member.phone || '',
        joinDate: member.joinDate || '',
        status: member.status || 'MEMBER',
        householdId: member.householdId ? String(member.householdId) : '',
        householdTouched: false
      }
      this.today = localISODate()
      this.resetFormErrors()
      this.formOpen = true
      this.loadHouseholds()
      // the dialog focuses the first field when it opens; "Change status..." wants the select instead
      if (focusStatus) setTimeout(() => document.getElementById('member-status')?.focus(), 0)
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
    showDeleteModal(member) {
      this.selectedMember = member
      this.deleteError = ''
      this.deleteOpen = true
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
      const message = error.message || 'Request failed'
      if (!fieldErrors.length) rest.push(message)
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
        // a new member with no household sends nothing; only an edit sends null to leave one
        if (!editing && request.householdId === null) delete request.householdId
        if (editing) {
          await api.updateMember(this.editingMember.id, request)
        } else {
          await api.createMember(request)
        }
        await this.reloadLists()
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
        await this.reloadLists()
        this.deleteOpen = false
        this.notify('success', 'Member archived', name)
      } catch (error) {
        console.error('Error archiving member:', error)
        this.deleteError = error.message || 'Request failed'
        this.notifyFailure('Could not archive member', error)
      } finally {
        this.deleting = false
      }
    },
    async toggleStatus(member) {
      const reactivating = member.status !== 'MEMBER'
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, status: reactivating ? 'MEMBER' : 'INACTIVE' }))
        await this.reloadLists()
        this.notify('success', reactivating ? 'Member reactivated' : 'Member deactivated', member.name)
      } catch (error) {
        console.error('Error toggling member status:', error)
        this.notifyFailure(reactivating ? 'Could not reactivate member' : 'Could not deactivate member', error)
      }
    },
    async restoreMember(member) {
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, status: 'MEMBER' }))
        await this.reloadLists()
        this.notify('success', 'Member restored', member.name)
      } catch (error) {
        console.error('Error restoring member:', error)
        this.notifyFailure('Could not restore member', error)
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
        // the archived list is not the full list the endpoint exports, so it always goes by ids
        const ids = this.showingArchived ? this.filteredMembers.map(member => member.id) : exportIds(this.filteredMembers, this.members)
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

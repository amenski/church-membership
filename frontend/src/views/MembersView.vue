<template>
  <div>
    <PageHead title="Members" lead="Everyone on the register: who is paid up and who is behind.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportMembers">
          <Icon name="download" :size="16" class="mr-1.5" />Export CSV
        </BaseButton>
        <BaseButton @click="showAddModal">
          <Icon name="plus" :size="16" class="mr-1.5" />Add member
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
        <Icon :name="datesOpen ? 'chevron-up' : 'chevron-down'" :size="16" />
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
        {{ countText }}
      </p>

      <!-- Archived (ADMIN only): what is hidden, with the two things an administrator can do about it -->
      <template v-if="showingArchived">
        <div role="note" class="mb-4 rounded-md border border-rule bg-paper px-4 py-2.5 text-sm text-ink">
          Archived members are hidden from the lists, dues, reminders and messages. Their payments and messages are kept. Only administrators see this view.
        </div>

        <table :class="TABLE">
          <caption class="sr-only">Archived members</caption>
          <thead>
            <tr class="border-b border-rule">
              <th scope="col" :class="TH">Member</th>
              <th scope="col" :class="TH">Household</th>
              <th scope="col" :class="TH">{{ stripRangeLabel(today.slice(0, 7)) }}, one square a month</th>
              <th scope="col" :class="TH">Last paid</th>
              <th scope="col" :class="TH">Archived</th>
              <th scope="col" :class="TH"><span class="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in filteredMembers" :key="member.id" class="border-b border-rule align-top">
              <td :class="[TD, 'max-w-0 w-[26%] py-3']">
                <div :class="[NAME, 'text-muted']">{{ member.name }}</div>
                <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              </td>
              <td :class="[TD, 'py-3 text-muted']">
                <template v-if="member.householdName">{{ member.householdName }}</template>
                <template v-else>None</template>
              </td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">
                <YearStrip v-if="paidByMember" v-bind="stripProps(member)" />
                <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">Months paid did not load</span></span>
              </td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">{{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : 'Never' }}</td>
              <td :class="[TD, 'py-3 whitespace-nowrap']">{{ archivedOn(member) }}</td>
              <td :class="[TD, 'py-3']">
                <div class="flex flex-wrap items-center justify-end gap-2">
                  <BaseButton variant="secondary" size="sm" :disabled="restoringId === member.id" @click="restoreMember(member)">
                    Restore<span class="sr-only"> {{ member.name }}</span>
                  </BaseButton>
                  <button type="button" :class="[DELETE_BUTTON, 'px-2.5 py-1 text-sm']" :disabled="hasPayments(member)" :aria-describedby="hasPayments(member) ? `keep-${member.id}` : undefined" @click="showPermanentModal(member)">
                    Delete for good<span class="sr-only"> {{ member.name }}</span>
                  </button>
                </div>
                <p v-if="hasPayments(member)" :id="`keep-${member.id}`" class="m-0 mt-1 text-right text-xs text-muted">Has payments, so it stays archived</p>
              </td>
            </tr>
          </tbody>
        </table>

        <ul class="m-0 flex list-none flex-col gap-3 p-0 md:hidden">
          <li v-for="member in filteredMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
            <div class="min-w-0">
              <div :class="[NAME, 'text-xl text-muted']">{{ member.name }}</div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1" /><span class="sr-only">Household: </span>{{ member.householdName }}</div>
              <div class="mt-1 text-sm text-muted tabular-nums">
                Last paid {{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : 'never' }} &middot; {{ archivedOn(member) }}
              </div>
            </div>
            <YearStrip v-if="paidByMember" size="large" v-bind="stripProps(member)" />
            <div class="flex flex-col gap-2">
              <div class="flex gap-2">
                <button type="button" :class="[PHONE_ACTION, 'flex-1 border border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint disabled:pointer-events-none disabled:opacity-65']" :disabled="restoringId === member.id" @click="restoreMember(member)">
                  Restore<span class="sr-only"> {{ member.name }}</span>
                </button>
                <button type="button" :class="[DELETE_BUTTON, PHONE_ACTION, 'flex-1']" :disabled="hasPayments(member)" :aria-describedby="hasPayments(member) ? `keep-card-${member.id}` : undefined" @click="showPermanentModal(member)">
                  Delete for good<span class="sr-only"> {{ member.name }}</span>
                </button>
              </div>
              <p v-if="hasPayments(member)" :id="`keep-card-${member.id}`" class="m-0 text-sm text-muted">Has payments, so it stays archived.</p>
            </div>
          </li>
        </ul>
      </template>

      <!-- md and up: ruled table -->
      <table v-else :class="TABLE">
        <caption class="sr-only">Members</caption>
        <thead>
          <tr class="border-b border-rule">
            <th v-for="column in columns" :key="column.label" scope="col" :aria-sort="ariaSort(column.sortKey)" :class="[TH, column.class]">
              <button v-if="column.sortKey" type="button" :class="SORT_BUTTON" @click="setSort(column.sortKey)">
                {{ column.label }}
                <Icon :name="sortIcon(column.sortKey)" :size="12" :class="sort.key === column.sortKey ? 'text-ink' : 'text-muted'" />
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
              <div v-if="member.householdName" class="text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1" /><span class="sr-only">Household: </span>{{ member.householdName }}</div>
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
              <YearStrip v-if="paidByMember" v-bind="stripProps(member)" />
              <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">Months paid did not load</span></span>
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

      <!-- Below md: one card per member -->
      <ul v-if="!showingArchived" class="m-0 flex list-none flex-col gap-3 p-0 md:hidden">
        <li v-for="member in filteredMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div :class="[NAME, 'text-xl']">{{ member.name }}</div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1" /><span class="sr-only">Household: </span>{{ member.householdName }}</div>
              <div class="mt-1 flex flex-wrap items-center gap-x-4">
                <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
                <span v-if="countsForDues(member)" :class="[duesClass(member), 'text-lg']">{{ duesText(member) }}</span>
              </div>
              <div class="mt-1 text-sm text-muted tabular-nums">
                <template v-if="member.phone">{{ member.phone }} &middot; </template>Joined {{ formatMemberDate(member.joinDate) }}
              </div>
            </div>
            <ActionMenu v-if="authStore.isStaff" :label="`More actions for ${member.name}`" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
          </div>
          <YearStrip v-if="paidByMember" size="large" v-bind="stripProps(member)" />
          <div v-if="member.phone || (authStore.isStaff && countsForDues(member))" class="flex gap-2">
            <a v-if="member.phone" :href="`tel:${member.phone.replace(/[^+\d]/g, '')}`" :class="[PHONE_ACTION, 'flex-1 border border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']">
              <Icon name="phone" :size="18" />Call<span class="sr-only"> {{ member.name }}</span>
            </a>
            <router-link v-if="authStore.isStaff && countsForDues(member)" :to="{ path: '/payments', query: { memberId: member.id } }" :class="[PHONE_ACTION, 'flex-[1.4] border border-teal bg-teal text-paper hover:bg-teal-hover']">
              Record payment<span class="sr-only"> for {{ member.name }}</span>
            </router-link>
          </div>
        </li>
      </ul>
    </template>

    <!-- Add and edit -->
    <MemberFormDialog v-model="formOpen" :member="editingMember" :focus-status="focusStatus" @saved="reloadLists" />

    <!-- Delete for good (ADMIN only, archived members) -->
    <ConfirmDialog
      v-model="permanentOpen"
      :title="`Delete ${selectedMember?.name || 'member'} for good?`"
      :message="`This removes ${selectedMember?.name || 'the member'} and cannot be undone. It only works for a member with no payments and no messages, such as one added by mistake.`"
      confirm-label="Delete for good"
      danger
      :busy="deletingPermanently"
      @confirm="deletePermanently"
    />

    <!-- Archive (ADMIN only) -->
    <MemberArchiveDialog v-model="deleteOpen" :member="selectedMember" @archived="reloadLists" />
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { paidMonthsByMember, stripRangeLabel } from '@/utils/yearStrip'
import { filterMembers, sortMembers, exportIds } from '@/utils/memberFilters'
import { buildMemberRequest } from '@/utils/memberPayload'
import { STATUS_OPTIONS, countsForDues, statusLabel, statusTone } from '@/utils/memberStatus'
import ActionMenu from '@/components/ActionMenu.vue'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import MemberArchiveDialog from '@/components/MemberArchiveDialog.vue'
import MemberFormDialog from '@/components/MemberFormDialog.vue'
import PageHead from '@/components/PageHead.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import YearStrip from '@/components/YearStrip.vue'
import TextButton from '@/components/TextButton.vue'
import { CONTROL, LABEL, NAME, SORT_BUTTON, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'

// A 44px tap target for the card's two actions
const PHONE_ACTION = 'flex min-h-11 items-center justify-center gap-2 rounded-md px-4 text-lg font-medium no-underline'
// "Delete for good": an outline in clay (the dialog holds the solid danger button)
const DELETE_BUTTON = 'inline-flex cursor-pointer items-center justify-center rounded-sm border border-clay bg-paper font-medium leading-normal text-clay hover:bg-clay-tint disabled:pointer-events-none disabled:border-rule disabled:text-muted disabled:opacity-65'
const EMPTY_FILTERS = { search: '', status: 'ALL', paymentStatus: 'ALL', joinedFrom: '', joinedTo: '' }

export default {
  name: 'MembersView',
  components: { ActionMenu, AlertBanner, BaseButton, ConfirmDialog, EmptyNote, Icon, MemberArchiveDialog, MemberFormDialog, PageHead, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore()
    }
  },
  data() {
    return {
      members: [],
      paidByMember: null,
      archivedMembers: [],
      archivedLoaded: false,
      loaded: false,
      loadError: false,
      filters: { ...EMPTY_FILTERS },
      datesOpen: false,
      sort: { key: null, direction: 'asc' },
      formOpen: false,
      focusStatus: false,
      editingMember: null,
      selectedMember: null,
      deleteOpen: false,
      permanentOpen: false,
      deletingPermanently: false,
      restoringId: null,
      today: localISODate(),
      PHONE_ACTION,
      DELETE_BUTTON,
      stripRangeLabel,
      TABLE,
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
    filteredMembers() {
      const filtered = filterMembers(this.source, this.filters)
      return this.sort.key ? sortMembers(filtered, this.sort.key, this.sort.direction) : filtered
    },
    countText() {
      const noun = this.showingArchived ? 'archived member' : 'member'
      const total = this.source.length
      return this.filteredMembers.length !== total
        ? `${this.filteredMembers.length} of ${total} ${noun}s`
        : `${total} ${noun}${total === 1 ? '' : 's'}`
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
        { label: `${stripRangeLabel(this.today.slice(0, 7))}, one square a month` },
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
    await Promise.all([this.loadMembers(), this.loadPayments()])
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
    // The paid months behind the year strip: one existing call, grouped by member. A failure only hides the strips.
    async loadPayments() {
      try {
        this.paidByMember = paidMonthsByMember(await api.getPayments())
      } catch (error) {
        console.error('Error loading payments for the year strip:', error)
        this.paidByMember = null
      }
    },
    stripProps(member) {
      return {
        joinDate: member.joinDate || '',
        paidMonths: this.paidByMember.get(member.id) || new Set(),
        currentMonth: this.today.slice(0, 7),
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member),
        muted: this.showingArchived,
        label: `Dues for ${member.name}, last 12 months`
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
      await Promise.all([this.loadMembers(), this.loadPayments()])
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
      if (this.sort.key !== key) return 'chevrons-up-down'
      return this.sort.direction === 'asc' ? 'caret-up' : 'caret-down'
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
      else if (key === 'delete') this.showDeleteModal(member)
    },
    showAddModal() {
      this.editingMember = null
      this.focusStatus = false
      this.today = localISODate()
      this.formOpen = true
    },
    showEditModal(member, focusStatus = false) {
      this.editingMember = member
      this.focusStatus = focusStatus
      this.today = localISODate()
      this.formOpen = true
    },
    showDeleteModal(member) {
      this.selectedMember = member
      this.deleteOpen = true
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
    // the archived list is not the normal list: the payments loaded for the strips say whether a member has history
    hasPayments(member) {
      return !!this.paidByMember?.get(member.id)?.size
    },
    // archivedAt is a date-time: the day is all the screen shows (the API does not say who archived)
    archivedOn(member) {
      return member.archivedAt ? this.formatMemberDate(member.archivedAt.slice(0, 10)) : ''
    },
    showPermanentModal(member) {
      this.selectedMember = member
      this.permanentOpen = true
    },
    async deletePermanently() {
      const { id, name } = this.selectedMember
      this.deletingPermanently = true
      try {
        await api.deleteMemberPermanently(id)
        await this.reloadLists()
        this.permanentOpen = false
        this.notify('success', 'Deleted for good', name)
      } catch (error) {
        console.error('Error deleting member for good:', error)
        this.permanentOpen = false
        if (error.response?.status === 409) {
          this.notify('error', 'Could not delete', `${name} has payments or messages, so they can only stay archived. Their history is kept.`)
        } else {
          this.notifyFailure('Could not delete member', error)
        }
      } finally {
        this.deletingPermanently = false
      }
    },
    async restoreMember(member) {
      this.restoringId = member.id
      try {
        await api.updateMember(member.id, buildMemberRequest({ ...member, status: 'MEMBER' }))
        await this.reloadLists()
        this.notify('success', 'Restored', member.name)
      } catch (error) {
        console.error('Error restoring member:', error)
        this.notifyFailure('Could not restore member', error)
      } finally {
        this.restoringId = null
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

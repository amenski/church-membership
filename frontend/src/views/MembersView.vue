<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead title="Members" compact band>
      <template v-if="authStore.isStaff" #actions>
        <!-- below lg Export CSV sits in the count line under the filters -->
        <BaseButton variant="secondary" class="max-lg:hidden" @click="exportMembers">
          <Icon name="download" :size="16" class="mr-1.5" />Export CSV
        </BaseButton>
        <BaseButton class="max-lg:min-h-11" @click="showAddModal">
          <Icon name="plus" :size="16" class="mr-1.5" />Add member
        </BaseButton>
      </template>
    </PageHead>

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The member list did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadMembers">Try again</BaseButton>
      </div>
    </AlertBanner>

    <!-- Filters: one compact row from lg up. On a phone or tablet: status, then search beside one "Filters" button
         that opens Dues, Sort by and the date pair; closed until asked for -->
    <form v-if="members.length || showingArchived" class="mb-3 grid grid-cols-[minmax(0,1fr)_auto] gap-3 lg:mb-6 lg:flex lg:flex-wrap lg:items-end" role="search" aria-label="Filter members" @submit.prevent>
      <div class="col-span-2 overflow-x-auto lg:basis-full lg:overflow-visible">
        <div role="group" aria-label="Filter by status" class="inline-flex">
          <button
            v-for="segment in statusSegments"
            :key="segment.value"
            type="button"
            :class="[SEGMENT, segmentShape(segment.value), filters.status === segment.value ? 'z-10 border-teal bg-teal text-paper hover:bg-teal-hover' : 'border-field bg-paper text-ink hover:bg-teal-tint']"
            :aria-pressed="filters.status === segment.value ? 'true' : 'false'"
            @click="filters.status = segment.value"
          >
            {{ segment.label }}<template v-if="segment.count !== null"> <span class="tabular-nums">{{ segment.count }}</span></template>
          </button>
        </div>
      </div>
      <div class="lg:min-w-60 lg:flex-1">
        <label for="filter-search" :class="[LABEL, 'max-lg:sr-only']">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search name, email or phone" autocomplete="off" :class="[CONTROL, 'max-lg:h-11']">
      </div>
      <button
        type="button"
        :class="['flex h-11 cursor-pointer items-center gap-1.5 rounded-sm border px-3 text-base font-medium whitespace-nowrap lg:hidden', panelFilterCount ? 'border-teal bg-teal-tint text-teal' : 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']"
        aria-controls="filter-panel"
        :aria-expanded="filtersOpen ? 'true' : 'false'"
        @click="filtersOpen = !filtersOpen"
      >
        Filters<template v-if="panelFilterCount"> ({{ panelFilterCount }})</template>
        <Icon :name="filtersOpen ? 'chevron-up' : 'chevron-down'" :size="16" />
      </button>
      <div id="filter-panel" :class="filtersOpen ? 'col-span-2 grid grid-cols-2 gap-3 lg:contents' : 'hidden lg:contents'">
        <div class="lg:w-40">
          <label for="filter-dues" :class="LABEL">Dues</label>
          <select id="filter-dues" v-model="filters.paymentStatus" :class="CONTROL">
            <option value="ALL">All</option>
            <option value="CURRENT">Paid up</option>
            <option value="OVERDUE">Behind</option>
          </select>
        </div>
        <div class="lg:w-40">
          <label for="filter-sort" :class="LABEL">Sort by</label>
          <select id="filter-sort" :value="sort.key" :class="CONTROL" @change="setSortOption($event.target.value)">
            <option v-for="option in SORT_OPTIONS" :key="option.key" :value="option.key">{{ option.label }}</option>
          </select>
        </div>
        <div class="lg:w-40">
          <label for="filter-from" :class="LABEL">Joined from</label>
          <input id="filter-from" v-model="filters.joinedFrom" type="date" :class="CONTROL">
        </div>
        <div class="lg:w-40">
          <label for="filter-to" :class="LABEL">Joined to</label>
          <input id="filter-to" v-model="filters.joinedTo" type="date" :class="CONTROL">
        </div>
      </div>
      <TextButton v-if="hasActiveFilters && filteredMembers.length" class="col-span-2 text-left max-lg:min-h-11 lg:col-span-1 lg:py-1.5" @click="clearFilters">Clear filters</TextButton>
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
      <div class="mb-2 flex items-center justify-between gap-3">
        <p class="m-0 text-sm text-muted" aria-live="polite">
          {{ countText }}
        </p>
        <!-- below lg only: from lg Export CSV is in the page header -->
        <TextButton v-if="authStore.isStaff" class="-my-2.5 min-h-11 lg:hidden" @click="exportMembers">Export CSV</TextButton>
      </div>

      <!-- Selection (STAFF+): the live region speaks the count, the bar holds what can be done with those members -->
      <p class="sr-only" role="status" aria-live="polite">{{ selectionAnnouncement }}</p>
      <div v-if="selectedMembers.length" role="region" aria-label="Selected members" class="sticky top-14 z-30 mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md border border-teal-line bg-teal-tint px-4 py-2.5">
        <span class="text-base font-semibold text-teal">{{ selectedMembers.length }} selected</span>
        <span class="min-w-0 text-sm text-ink [overflow-wrap:anywhere]">{{ selectedNames }}</span>
        <TextButton @click="selectedIds = []">Clear selection</TextButton>
        <div class="flex flex-wrap gap-2 md:ml-auto">
          <BaseButton variant="secondary" to="/communications">Send message</BaseButton>
          <BaseButton variant="secondary" @click="exportSelected">Export selected</BaseButton>
          <BaseButton variant="secondary" :disabled="!inactiveTargets.length" @click="bulkAction = 'inactive'">Mark inactive</BaseButton>
          <button v-if="authStore.isAdmin" type="button" :class="[DELETE_BUTTON, 'min-h-(--control-h) px-3 py-1.5 text-base']" @click="bulkAction = 'archive'">Archive</button>
        </div>
      </div>
      <label v-if="canSelect" class="mb-2 flex min-h-11 cursor-pointer items-center gap-3 text-base text-ink lg:hidden">
        <input type="checkbox" :class="CHECKBOX_PHONE" :checked="allSelected" :indeterminate="someSelected" @change="toggleAll($event.target.checked)">
        Select all {{ filteredMembers.length }} shown
      </label>

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

        <ul class="m-0 grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2 lg:hidden">
          <li v-for="member in filteredMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
            <div class="min-w-0">
              <div :class="[NAME, 'text-xl text-muted']">{{ member.name }}</div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="flex items-center text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1 shrink-0" /><span class="sr-only">Household: </span>{{ member.householdName }}</div>
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

      <!-- lg and up: ruled table. Between lg and xl it keeps a minimum width and scrolls sideways inside its own box rather than squeezing the columns -->
      <div v-else class="hidden overflow-x-auto lg:block">
      <table :class="[TABLE, 'lg:min-w-[56rem] xl:min-w-0']">
        <caption class="sr-only">Members</caption>
        <thead>
          <tr class="border-b border-rule">
            <th v-if="canSelect" scope="col" :class="[TH, 'w-8']">
              <input type="checkbox" :class="CHECKBOX" :checked="allSelected" :indeterminate="someSelected" :aria-label="`Select all ${filteredMembers.length} shown`" @change="toggleAll($event.target.checked)">
            </th>
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
          <tr v-for="member in filteredMembers" :key="member.id" :class="['h-(--row-h) border-b border-rule', isSelected(member) ? 'bg-teal-tint' : '']">
            <td v-if="canSelect" :class="TD">
              <input type="checkbox" :class="CHECKBOX" :checked="isSelected(member)" :aria-label="`Select ${member.name}`" @change="toggleSelected(member, $event.target.checked)">
            </td>
            <td :class="[TD, 'max-w-0 w-[26%]']">
              <div :class="NAME"><router-link :to="`/members/${member.id}`">{{ member.name }}</router-link></div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <!-- the Phone column is hidden between lg and xl: its number sits under the email instead -->
              <div v-if="member.phone" class="whitespace-nowrap text-sm text-muted xl:hidden">{{ member.phone }}</div>
            </td>
            <td :class="[TD, 'max-w-0 w-[14%] [overflow-wrap:anywhere]']">
              <template v-if="member.householdName">{{ member.householdName }}</template>
              <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">No household</span></span>
            </td>
            <td :class="[TD, 'whitespace-nowrap max-xl:hidden']">
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
            <td :class="[TD, 'whitespace-nowrap']">{{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : 'Never' }}</td>
            <td v-if="authStore.isStaff" :class="[TD, 'text-right']">
              <ActionMenu :label="`More actions for ${member.name}`" :items="menuItems(member)" @select="key => onMenuSelect(key, member)" />
            </td>
          </tr>
        </tbody>
      </table>
      </div>

      <!-- Below lg: one card per member, two across from md -->
      <ul v-if="!showingArchived" class="m-0 grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2 lg:hidden">
        <li v-for="member in filteredMembers" :key="member.id" class="flex flex-col gap-3 rounded-lg border border-rule bg-paper px-4 py-3.5">
          <div class="flex items-start justify-between gap-2">
            <label v-if="canSelect" class="-ml-2 -mt-1.5 flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center">
              <input type="checkbox" :class="CHECKBOX_PHONE" :checked="isSelected(member)" :aria-label="`Select ${member.name}`" @change="toggleSelected(member, $event.target.checked)">
            </label>
            <div class="min-w-0 flex-1">
              <div :class="[NAME, 'text-xl']"><router-link :to="`/members/${member.id}`" class="inline-block py-2 -my-2">{{ member.name }}</router-link></div>
              <div v-if="member.email" class="text-sm text-muted [overflow-wrap:anywhere]">{{ member.email }}</div>
              <div v-if="member.householdName" class="flex items-center text-sm text-muted [overflow-wrap:anywhere]"><Icon name="home" :size="14" class="mr-1 shrink-0" /><span class="sr-only">Household: </span>{{ member.householdName }}</div>
              <div class="mt-1 flex flex-wrap items-center gap-x-4">
                <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
                <span v-if="countsForDues(member)" :class="[duesClass(member), 'text-lg']">{{ duesText(member) }}</span>
              </div>
              <div class="mt-1 text-sm text-muted tabular-nums">
                <template v-if="member.phone">{{ member.phone }} &middot; </template>Joined {{ formatMemberDate(member.joinDate) }} &middot; Last paid {{ member.lastPaymentDate ? formatMemberDate(member.lastPaymentDate) : 'never' }}
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

    <!-- Bulk: Mark inactive and Archive for the selected members -->
    <ConfirmDialog
      :model-value="bulkAction === 'inactive'"
      :title="`Mark ${inactiveTargets.length} ${inactiveTargets.length === 1 ? 'member' : 'members'} inactive?`"
      :message="inactiveMessage"
      confirm-label="Mark inactive"
      :busy="bulkBusy"
      @update:model-value="open => { if (!open) bulkAction = null }"
      @confirm="markSelectedInactive"
    />
    <ConfirmDialog
      :model-value="bulkAction === 'archive'"
      :title="`Archive ${selectedMembers.length} ${selectedMembers.length === 1 ? 'member' : 'members'}?`"
      :message="`This hides ${selectedMembers.length === 1 ? 'this member' : 'these members'} from the lists. Their payments and messages are kept.`"
      :confirm-label="`Archive ${selectedMembers.length === 1 ? 'member' : selectedMembers.length + ' members'}`"
      danger
      :busy="bulkBusy"
      @update:model-value="open => { if (!open) bulkAction = null }"
      @confirm="archiveSelected"
    />

    <!-- Archive (ADMIN only) -->
    <MemberArchiveDialog v-model="deleteOpen" :member="selectedMember" @archived="reloadLists" />
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { paidMonthsByMember, stripRangeLabel } from '@/utils/yearStrip'
import { STATUS_SEGMENTS, filterMembers, sortMembers, statusCounts, exportIds } from '@/utils/memberFilters'
import { membersCsv } from '@/utils/memberCsv'
import { buildMemberRequest } from '@/utils/memberPayload'
import { countsForDues, statusLabel, statusTone } from '@/utils/memberStatus'
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
import { CONTROL, LABEL, NAME, SORT_BUTTON, TABLE_FROM_LG as TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'

// A 44px tap target for the card's two actions
const PHONE_ACTION = 'flex min-h-11 items-center justify-center gap-2 rounded-md px-4 text-lg font-medium no-underline'
// "Delete for good": an outline in clay (the dialog holds the solid danger button)
const DELETE_BUTTON = 'inline-flex cursor-pointer items-center justify-center rounded-sm border border-clay bg-paper font-medium leading-normal text-clay hover:bg-clay-tint disabled:pointer-events-none disabled:border-rule disabled:text-muted disabled:opacity-65'
// One button of the Status segmented control; segmentShape rounds the two ends and joins the borders
const SEGMENT = 'relative -ml-px first:ml-0 inline-flex min-h-11 shrink-0 cursor-pointer items-center gap-1 border px-3 text-base font-medium whitespace-nowrap lg:min-h-(--control-h)'
// The Sort by control: the first click on a header starts ascending, but here "Most behind" and
// "Joined" mean the most behind and the newest first
const SORT_OPTIONS = [
  { key: 'name', label: 'Name', direction: 'asc' },
  { key: 'consecutiveMonthsMissed', label: 'Most behind', direction: 'desc' },
  { key: 'joinDate', label: 'Joined', direction: 'desc' }
]
// A row checkbox (16px; its cell is the tap area on a desktop) and the 44px-box one on a phone card
const CHECKBOX = 'h-4 w-4 cursor-pointer accent-teal'
const CHECKBOX_PHONE = 'h-5 w-5 cursor-pointer accent-teal'
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
      filtersOpen: false,
      sort: { key: 'name', direction: 'asc' },
      selectedIds: [],
      bulkAction: null,
      bulkBusy: false,
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
      SEGMENT,
      CHECKBOX,
      CHECKBOX_PHONE,
      SORT_OPTIONS,
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
    // the segments with their counts; the archived list loads on demand, so its count shows once it has
    statusSegments() {
      const counts = statusCounts(this.members)
      const segments = STATUS_SEGMENTS.map(segment => ({ ...segment, count: counts[segment.value] }))
      if (this.authStore.isAdmin) segments.push({ value: 'ARCHIVED', label: 'Archived', count: this.archivedLoaded ? this.archivedMembers.length : null })
      return segments
    },
    filteredMembers() {
      const filtered = filterMembers(this.source, this.filters)
      return this.sort.key ? sortMembers(filtered, this.sort.key, this.sort.direction) : filtered
    },
    // Selecting is for STAFF+ and for the normal list; only the rows on screen count as selected
    canSelect() {
      return this.authStore.isStaff && !this.showingArchived
    },
    selectedMembers() {
      return this.canSelect ? this.filteredMembers.filter(member => this.selectedIds.includes(member.id)) : []
    },
    allSelected() {
      return this.canSelect && this.selectedMembers.length === this.filteredMembers.length
    },
    someSelected() {
      return this.selectedMembers.length > 0 && !this.allSelected
    },
    // Mark inactive only changes a Member: an inactive, transferred or deceased one is left as it is
    inactiveTargets() {
      return this.selectedMembers.filter(countsForDues)
    },
    inactiveMessage() {
      const skipped = this.selectedMembers.length - this.inactiveTargets.length
      const base = 'They stop counting for dues, reminders and messages. You can mark them active again later.'
      return skipped ? `${base} ${skipped} selected ${skipped === 1 ? 'member is' : 'members are'} not a Member now and will be left as ${skipped === 1 ? 'it is' : 'they are'}.` : base
    },
    // the first three names, then how many more
    selectedNames() {
      const names = this.selectedMembers.map(member => member.name)
      return names.length > 3 ? `${names.slice(0, 3).join(', ')} and ${names.length - 3} more` : names.join(', ')
    },
    selectionAnnouncement() {
      return this.selectedMembers.length ? `${this.selectedMembers.length} selected` : ''
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
    // what the phone "Filters" button holds that is set: Dues and the two dates (status and search stay in view)
    panelFilterCount() {
      const f = this.filters
      return (f.paymentStatus !== 'ALL' ? 1 : 0) + (f.joinedFrom ? 1 : 0) + (f.joinedTo ? 1 : 0)
    },
    columns() {
      return [
        { label: 'Name', sortKey: 'name' },
        { label: 'Household' },
        { label: 'Phone', class: 'max-xl:hidden' },
        { label: 'Joined', sortKey: 'joinDate' },
        { label: 'Status' },
        { label: `${stripRangeLabel(this.today.slice(0, 7))}, one square a month` },
        { label: 'Dues', sortKey: 'consecutiveMonthsMissed' },
        { label: 'Last paid' }
      ]
    }
  },
  watch: {
    'filters.status'(status) {
      if (status === 'ARCHIVED' && this.authStore.isAdmin) this.loadArchived()
    },
    // the top bar search pushes /members?search= while this screen is already open
    '$route.query.search'(search) {
      if (typeof search === 'string') this.filters.search = search
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
    isSelected(member) {
      return this.selectedIds.includes(member.id)
    },
    toggleSelected(member, checked) {
      this.selectedIds = checked
        ? [...this.selectedIds, member.id]
        : this.selectedIds.filter(id => id !== member.id)
    },
    // the header checkbox: every row on screen, nothing hidden by a filter
    toggleAll(checked) {
      const shown = this.filteredMembers.map(member => member.id)
      this.selectedIds = checked
        ? [...new Set([...this.selectedIds, ...shown])]
        : this.selectedIds.filter(id => !shown.includes(id))
    },
    // One call per member, one after the other. Failures never stop the rest: the ones that worked are
    // reported with the ones that did not, and only the failed members stay selected.
    async runBulk(targets, call, past) {
      this.bulkBusy = true
      const failed = []
      let firstError = null
      for (const member of targets) {
        try {
          await call(member)
        } catch (error) {
          console.error(`Error: ${past} ${member.name}:`, error)
          failed.push(member)
          firstError = firstError || error
        }
      }
      const done = targets.length - failed.length
      this.selectedIds = failed.map(member => member.id)
      this.bulkAction = null
      try {
        await this.reloadLists()
      } finally {
        this.bulkBusy = false
      }
      const noun = n => `${n} ${n === 1 ? 'member' : 'members'}`
      if (!failed.length) {
        this.notify('success', `${past[0].toUpperCase()}${past.slice(1)}`, noun(done))
      } else if (firstError.response?.status !== 403) {
        this.notify('error', `${done} of ${noun(targets.length)} ${past}`, `Could not change: ${failed.map(member => member.name).join(', ')}. ${firstError.message || 'Request failed'}`)
      }
    },
    markSelectedInactive() {
      return this.runBulk(this.inactiveTargets, member => api.updateMember(member.id, buildMemberRequest({ ...member, status: 'INACTIVE' })), 'marked inactive')
    },
    archiveSelected() {
      return this.runBulk(this.selectedMembers, member => api.deleteMember(member.id), 'archived')
    },
    exportSelected() {
      downloadBlob(membersCsv(this.selectedMembers), `members_selected_${new Date().toISOString().split('T')[0]}.csv`)
    },
    setSortOption(key) {
      this.sort = { key, direction: SORT_OPTIONS.find(option => option.key === key).direction }
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
    segmentShape(value) {
      const last = this.statusSegments[this.statusSegments.length - 1].value
      return [value === 'ALL' ? 'rounded-l-sm' : '', value === last ? 'rounded-r-sm' : '']
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

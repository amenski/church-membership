<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead :title="$t('nav.households')" :lead="$t('households.lead')" band>
      <template v-if="authStore.isStaff" #actions>
        <BaseButton @click="showAddModal">
          <Icon name="plus" :size="16" class="mr-1.5" />{{ $t('households.addHousehold') }}
        </BaseButton>
      </template>
    </PageHead>

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ $t('households.loadError') }}</span>
        <BaseButton variant="secondary" size="sm" @click="loadHouseholds">{{ $t('common.tryAgain') }}</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">{{ $t('households.loading') }}</p>

    <!-- Empty state -->
    <div v-if="loaded && !loadError && !households.length">
      <EmptyNote>{{ $t('households.empty') }} <template v-if="authStore.isStaff">{{ $t('households.emptyStaff') }}</template><template v-else>{{ $t('households.emptyMember') }}</template></EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="showAddModal">{{ $t('households.addHousehold') }}</BaseButton>
    </div>

    <!-- From lg: the list on the left, the chosen household on the right. Below lg: one or the other (the URL's ?id= says which) -->
    <div v-if="households.length" class="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,22rem)_minmax(0,1fr)] lg:items-start">
      <section :class="['min-w-0', selectedId && 'max-lg:hidden']" :aria-label="$t('households.listAria')">
        <form class="mb-3" role="search" :aria-label="$t('households.searchAria')" @submit.prevent>
          <label for="household-search" :class="LABEL">{{ $t('common.search') }}</label>
          <input id="household-search" v-model="search" type="search" :placeholder="$t('households.searchPlaceholder')" autocomplete="off" :class="CONTROL">
        </form>

        <div v-if="!visibleHouseholds.length">
          <EmptyNote>{{ $t('households.noMatch', { search: search.trim() }) }}</EmptyNote>
          <BaseButton variant="secondary" class="mt-2" @click="search = ''">{{ $t('households.clearSearch') }}</BaseButton>
        </div>
        <template v-else>
          <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
            {{ visibleHouseholds.length !== households.length ? $t('households.countOf', { shown: visibleHouseholds.length, total: households.length }) : $t('households.countHousehold', households.length) }}
          </p>
          <ul class="m-0 list-none overflow-hidden rounded-md border border-rule bg-paper p-0">
            <li v-for="household in pagedHouseholds" :key="household.id" class="border-b border-rule last:border-b-0">
              <button
                type="button"
                :aria-current="String(household.id) === selectedId ? 'true' : undefined"
                :class="['block min-h-(--list-row-h) w-full cursor-pointer border-0 px-4 py-3 text-left font-sans text-(length:--text-body) text-ink', String(household.id) === selectedId ? 'bg-teal-tint ring-2 ring-teal ring-inset' : 'bg-paper hover:bg-teal-tint']"
                @click="openDetail(household)"
              >
                <span class="flex items-baseline justify-between gap-3">
                  <span :class="NAME">{{ household.name }}</span>
                  <span v-if="household.city" class="shrink-0 text-xs text-muted [overflow-wrap:anywhere]">{{ household.city }}</span>
                </span>
                <span class="block text-sm text-muted tabular-nums">{{ memberCountText(household.memberCount) }}</span>
              </button>
            </li>
          </ul>
          <!-- the list column is 22rem wide from lg: there the numbered buttons give way to "Page 4 of 12" -->
          <Pager v-bind="pagerProps" :page-sizes="HOUSEHOLD_PAGE_SIZES" :compact="wide" class="mt-3" @update:page="setPage" @update:page-size="setPageSize" />
        </template>
      </section>

      <!-- Detail -->
      <section v-if="selectedId" class="min-w-0" aria-labelledby="household-title">
        <TextButton class="mb-2 inline-flex min-h-11 items-center lg:hidden" @click="closeDetail">{{ $t('households.allHouseholds') }}</TextButton>
        <AlertBanner v-if="detailError">{{ detailError }}</AlertBanner>
        <p v-if="detailLoading" class="m-0 py-3 text-muted" role="status">{{ $t('households.loadingDetail') }}</p>
        <div v-else-if="detail" :class="[CARD, 'flex flex-col gap-5']">
          <div class="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
            <div class="min-w-0">
              <h2 id="household-title" class="m-0 mb-1 text-xl font-semibold text-ink [overflow-wrap:anywhere]">{{ detail.name }}</h2>
              <address v-if="addressLines.length" class="m-0 text-base not-italic [overflow-wrap:anywhere]">
                <div v-for="line in addressLines" :key="line">{{ line }}</div>
              </address>
              <p v-else class="m-0 text-base text-muted">{{ $t('households.noAddress') }}</p>
            </div>
            <div v-if="authStore.isStaff" class="flex flex-wrap gap-2">
              <BaseButton variant="secondary" @click="showEditModal">{{ $t('households.editHousehold') }}</BaseButton>
              <BaseButton v-if="authStore.isAdmin" variant="danger" @click="showDeleteConfirm">{{ $t('households.deleteHousehold') }}</BaseButton>
            </div>
          </div>

          <p v-if="owedNotice" class="m-0 rounded-md border border-ochre-line bg-ochre-tint px-4 py-3 text-base text-ochre-text">{{ owedNotice }}</p>

          <div v-if="detail.notes">
            <h3 :class="SUBHEAD">{{ $t('households.notes') }}</h3>
            <p class="m-0 text-base whitespace-pre-line [overflow-wrap:anywhere]">{{ detail.notes }}</p>
          </div>

          <div>
            <h3 :class="SUBHEAD">{{ $t('households.membersHeading', { n: memberRows.length }) }}</h3>
            <p v-if="memberRows.length" class="m-0 mb-2 text-sm text-muted">{{ $t('households.eachPays') }}</p>
            <ul v-if="memberRows.length" class="m-0 list-none border-t border-rule p-0">
              <li v-for="member in memberRows" :key="member.id" class="flex min-h-11 flex-wrap items-center gap-x-4 gap-y-2 border-b border-rule py-3">
                <router-link :to="`/members/${member.id}`" class="min-w-0 flex-[1_1_10rem] font-medium [overflow-wrap:anywhere]">{{ member.name }}</router-link>
                <StatusBadge :tone="statusTone(member.status)">{{ $t(statusKey(member.status)) }}</StatusBadge>
                <YearStrip v-if="member.strip" v-bind="member.strip" :muted="member.status === 'ARCHIVED'" />
                <span class="min-w-28 text-right max-sm:ml-auto">
                  <StatusLabel v-if="member.behindText" :tone="member.behindTone">{{ member.behindText }}</StatusLabel>
                </span>
              </li>
            </ul>
            <p v-else class="m-0 text-base text-muted">
              {{ $t('households.noMembers') }}<template v-if="authStore.isStaff">{{ $t('households.noMembersStaff') }}</template>
            </p>
          </div>

          <div>
            <h3 :class="SUBHEAD">{{ $t('households.dependentsHeading', { n: dependents.length }) }}</h3>
            <p class="m-0 mb-2 text-sm text-muted">{{ $t('households.dependentsNote') }}</p>
            <ul v-if="dependents.length" class="m-0 list-none border-t border-rule p-0">
              <li v-for="person in dependents" :key="person.id" class="border-b border-rule py-2">
                <div class="min-h-6 font-medium [overflow-wrap:anywhere]">{{ person.name }}</div>
                <div v-if="person.birthDate" class="text-sm text-muted">{{ $t('households.born', { date: formatDay(person.birthDate) }) }}<template v-if="personAge(person.birthDate)"> &middot; {{ personAge(person.birthDate) }}</template></div>
                <div v-if="authStore.isStaff" class="-mx-2 mt-1 flex flex-wrap">
                  <button type="button" :class="ROW_ACTION" @click="showEditPerson(person)">{{ $t('households.edit') }}<span class="sr-only"> {{ person.name }}</span></button>
                  <button type="button" :class="ROW_ACTION" @click="showMembershipModal(person)">{{ $t('households.makeMember') }}<span class="sr-only">: {{ person.name }}</span></button>
                  <button v-if="authStore.isAdmin" type="button" :class="ROW_ACTION_DANGER" @click="showPersonDeleteConfirm(person)">{{ $t('households.delete') }}<span class="sr-only"> {{ person.name }}</span></button>
                </div>
              </li>
            </ul>
            <template v-else>
              <p class="m-0 text-base text-muted">
                {{ $t('households.nobodyHere') }}<template v-if="authStore.isStaff">{{ $t('households.nobodyHereStaff') }}</template><template v-else>{{ $t('households.nobodyHereMember') }}</template>
              </p>
            </template>
            <BaseButton v-if="authStore.isStaff" variant="secondary" class="mt-3 max-sm:min-h-11" @click="showAddPerson">
              <Icon name="plus" :size="16" class="mr-1.5" />{{ $t('households.addPerson') }}
            </BaseButton>
          </div>
        </div>
      </section>
    </div>

    <!-- Add and edit -->
    <BaseModal v-model="formOpen" :title="editing ? $t('households.formTitleEdit') : $t('households.formTitleAdd')" size="md">
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <form id="household-form" class="flex flex-col gap-4" novalidate @submit.prevent="saveHousehold">
        <BaseInput id="household-name" v-model="form.name" :label="$t('households.name')" autocomplete="off" :hint="$t('households.nameHint')" :error="formErrors.name" />
        <BaseInput id="household-address1" v-model="form.addressLine1" :label="$t('households.address')" autocomplete="off" :hint="$t('households.optional')" :error="formErrors.addressLine1" />
        <BaseInput id="household-address2" v-model="form.addressLine2" :label="$t('households.address2')" autocomplete="off" :error="formErrors.addressLine2" />
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <BaseInput id="household-city" v-model="form.city" :label="$t('households.city')" autocomplete="off" :error="formErrors.city" />
          <BaseInput id="household-postal" v-model="form.postalCode" :label="$t('households.postalCode')" autocomplete="off" :error="formErrors.postalCode" />
        </div>
        <BaseTextarea id="household-notes" v-model="form.notes" :label="$t('households.notes')" :rows="3" :max="2000" :hint="$t('households.notesHint')" :error="formErrors.notes" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="saving" @click="formOpen = false">{{ $t('common.cancel') }}</BaseButton>
        <BaseButton type="submit" form="household-form" :disabled="saving" :aria-busy="saving ? 'true' : undefined">
          {{ saving ? $t('common.saving') : editing ? $t('common.saveChanges') : $t('households.formTitleAdd') }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Delete (ADMIN only) -->
    <ConfirmDialog
      v-model="deleteOpen"
      :title="$t('households.deleteTitle', { name: selected?.name || $t('nav.households') })"
      :message="$t('households.deleteMessage')"
      :confirm-label="$t('households.deleteHousehold')"
      danger
      :busy="deleting"
      @confirm="deleteHousehold"
    />

    <!-- Add and edit a person (STAFF and up) -->
    <BaseModal v-model="personFormOpen" :title="personEditing ? $t('households.personTitleEdit') : $t('households.personTitleAdd')" size="md">
      <AlertBanner v-if="personFormError">{{ personFormError }}</AlertBanner>
      <form id="person-form" class="flex flex-col gap-4" novalidate @submit.prevent="savePerson">
        <p class="m-0 text-sm text-muted">{{ $t('households.householdLabel') }} <span class="font-medium text-ink [overflow-wrap:anywhere]">{{ selected?.name }}</span></p>
        <BaseInput id="person-name" v-model="personForm.name" :label="$t('households.name')" autocomplete="off" :error="personErrors.name" />
        <BaseInput id="person-email" v-model="personForm.email" :label="$t('households.personEmail')" type="email" autocomplete="off" :hint="$t('households.optional')" :error="personErrors.email" />
        <BaseInput id="person-phone" v-model="personForm.phone" :label="$t('households.personPhone')" type="tel" autocomplete="off" :hint="$t('profile.phoneHint')" :error="personErrors.phone" />
        <BaseInput id="person-birth" v-model="personForm.birthDate" :label="$t('households.birthDate')" type="date" :max="yesterday" :hint="$t('households.optional')" :error="personErrors.birthDate" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="personSaving" @click="personFormOpen = false">{{ $t('common.cancel') }}</BaseButton>
        <BaseButton type="submit" form="person-form" :disabled="personSaving" :aria-busy="personSaving ? 'true' : undefined">
          {{ personSaving ? $t('common.saving') : personEditing ? $t('common.saveChanges') : $t('households.personTitleAdd') }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Make a member (STAFF and up) -->
    <BaseModal v-model="membershipOpen" :title="$t('households.membershipTitle', { name: personTarget?.name || $t('households.thisPerson') })" size="sm">
      <AlertBanner v-if="membershipError">{{ membershipError }}</AlertBanner>
      <form id="membership-form" class="flex flex-col gap-4" novalidate @submit.prevent="startMembership">
        <BaseSelect id="membership-status" v-model="membershipForm.status" :label="$t('households.status')" :hint="$t('households.statusHint')" :error="membershipErrors.status">
          <option v-for="option in NEW_MEMBER_STATUS_OPTIONS" :key="option.value" :value="option.value">{{ $t(option.labelKey) }}</option>
        </BaseSelect>
        <BaseInput id="membership-joined" v-model="membershipForm.joinDate" :label="$t('households.joinedOn')" type="date" :max="today" :error="membershipErrors.joinDate" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="membershipSaving" @click="membershipOpen = false">{{ $t('common.cancel') }}</BaseButton>
        <BaseButton type="submit" form="membership-form" :disabled="membershipSaving" :aria-busy="membershipSaving ? 'true' : undefined">
          {{ membershipSaving ? $t('common.saving') : $t('households.makeAMember') }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Delete a person (ADMIN only) -->
    <ConfirmDialog
      v-model="personDeleteOpen"
      :title="$t('households.personDeleteTitle', { name: personTarget?.name || $t('households.thisPerson') })"
      :message="$t('households.personDeleteMessage')"
      :confirm-label="$t('households.personDeleteConfirm')"
      danger
      :busy="personDeleting"
      @confirm="deletePerson"
    />
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { formatDate, isValidEmail, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { NEW_MEMBER_STATUS_OPTIONS, statusKey, statusTone } from '@/utils/memberStatus'
import { buildMembershipRequest, buildPersonRequest, ageText } from '@/utils/person'
import { isValidPhone } from '@/utils/phoneRules'
import { paidMonthsFromMap } from '@/utils/yearStrip'
import { clampPage, pageSlice } from '@/utils/paging'
import { queryPaging } from '@/utils/queryPaging'
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
import Pager from '@/components/Pager.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'

import { CARD, CONTROL, LABEL, NAME } from '@/ui/classes'
const SUBHEAD = 'm-0 mb-1 font-sans text-[0.9375rem] font-medium text-muted'
// a text action in a person's row: 44px tall, so it is easy to hit on a phone
const ROW_ACTION_BASE = 'inline-flex min-h-11 cursor-pointer items-center border-0 bg-transparent px-2 font-sans text-sm font-medium underline underline-offset-[3px]'
const ROW_ACTION = `${ROW_ACTION_BASE} text-teal hover:text-teal-hover`
const ROW_ACTION_DANGER = `${ROW_ACTION_BASE} text-clay hover:text-clay-hover`

// Same breakpoint as the shell (lg): from here the list and the household sit side by side
const WIDE = '(min-width: 62rem)'

// The list shows 10 households a page; the size select offers three sizes
const HOUSEHOLD_PAGE_SIZES = [10, 25, 50]
const HOUSEHOLD_DEFAULT_SIZE = 10

const EMPTY_FORM = { name: '', addressLine1: '', addressLine2: '', city: '', postalCode: '', notes: '' }
const FIELDS = Object.keys(EMPTY_FORM)
const EMPTY_ERRORS = { ...EMPTY_FORM }

const EMPTY_PERSON = { name: '', email: '', phone: '', birthDate: '' }
const EMPTY_PERSON_ERRORS = { ...EMPTY_PERSON }
const EMPTY_MEMBERSHIP_ERRORS = { status: '', joinDate: '' }

export default {
  name: 'HouseholdsView',
  mixins: [queryPaging({ defaultSize: HOUSEHOLD_DEFAULT_SIZE, sizes: HOUSEHOLD_PAGE_SIZES })],
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, ConfirmDialog, EmptyNote, Icon, PageHead, Pager, StatusBadge, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore(),
      CARD, LABEL, CONTROL, NAME, SUBHEAD, ROW_ACTION, ROW_ACTION_DANGER, NEW_MEMBER_STATUS_OPTIONS, HOUSEHOLD_PAGE_SIZES,
      ageText,
      statusKey,
      statusTone
    }
  },
  data() {
    return {
      households: [],
      loaded: false,
      loadError: false,
      search: '',
      detail: null,
      detailLoading: false,
      detailError: '',
      detailToken: 0,
      wide: true,
      // for the strips and the dues notice: id -> member (months missed, join date) and id -> paid months; null when they did not load
      membersById: null,
      paidByMember: null,
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
    currentPage() {
      return clampPage(this.page, this.visibleHouseholds.length, this.pageSize)
    },
    // the rows on screen: one page of the list. The open household is chosen by ?id= and does not depend on the page
    pagedHouseholds() {
      return pageSlice(this.visibleHouseholds, this.currentPage, this.pageSize)
    },
    pagerProps() {
      return { page: this.currentPage, pageSize: this.pageSize, total: this.visibleHouseholds.length }
    },
    // /households?id=<id> chooses the household (a member's page links here)
    selectedId() {
      const id = this.$route?.query?.id
      return typeof id === 'string' && /^\d+$/.test(id) ? id : ''
    },
    // the open household, or its row in the list while it loads
    selected() {
      return this.detail || this.households.find(household => String(household.id) === this.selectedId) || null
    },
    // each member of the open household with the strip and the words that say where they stand (the strip rule: utils/yearStrip.js)
    memberRows() {
      const currentMonth = this.today.slice(0, 7)
      return (this.detail?.members || []).map(member => {
        const known = this.membersById?.get(member.id)
        const owes = member.status === 'MEMBER'
        const missed = known?.consecutiveMonthsMissed || 0
        const strip = known && this.paidByMember
          ? {
              joinDate: known.joinDate || '',
              paidMonths: this.paidByMember.get(member.id) || new Set(),
              currentMonth,
              monthsMissed: missed,
              countsForDues: owes,
              label: this.$t('strip.duesFor', { name: member.name })
            }
          : null
        const behind = owes && known && missed > 0
        return {
          ...member,
          strip,
          missed: owes ? missed : 0,
          behindText: behind ? monthsBehind(missed, this.$t) : owes && known ? this.$t('dues.badgePaid') : '',
          behindTone: behind ? 'behind' : 'paid'
        }
      })
    },
    // "Dues are per member: 2 members in this household owe 3 months in total." Nothing when nobody is behind or the figures did not load.
    owedNotice() {
      const behind = this.memberRows.filter(member => member.missed > 0)
      if (!behind.length) return ''
      const months = behind.reduce((sum, member) => sum + member.missed, 0)
      const notice = this.$t('households.duesPerMember', {
        who: this.$t('households.whoOwes', behind.length),
        months: this.$t('households.countMonth', months)
      })
      return this.dependents.length ? `${notice} ${this.$t('households.dependentsOweNothing')}` : notice
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
  watch: {
    selectedId() {
      this.loadDetail()
    },
    // a new search: back to page 1
    search() {
      this.resetPage()
    },
    // the list changed length (loaded, one added or deleted): a page beyond the last becomes the last
    'visibleHouseholds.length'(length) {
      if (this.loaded) this.settlePage(length)
    }
  },
  async created() {
    this.wideQuery = window.matchMedia?.(WIDE)
    this.wide = this.wideQuery ? this.wideQuery.matches : true
    this.wideQuery?.addEventListener('change', this.onWide)
    this.loadDetail()
    this.loadMembers()
    this.loadPaidMonths()
    await this.loadHouseholds()
    this.ensureSelection()
  },
  beforeUnmount() {
    this.wideQuery?.removeEventListener('change', this.onWide)
  },
  methods: {
    onWide(event) {
      this.wide = event.matches
      this.ensureSelection()
    },
    // Side by side there is always a household on the right: the first one until the user chooses
    ensureSelection() {
      if (this.wide && !this.selectedId && this.households.length) this.$router.replace({ query: { ...this.$route.query, id: String(this.households[0].id) } })
    },
    // The members and their paid months behind the strips (GET /members and /payments/paid-months, the calls the Overview makes too). A failure only hides the strips and the notice.
    async loadMembers() {
      try {
        const members = await api.getMembers()
        this.membersById = new Map((Array.isArray(members) ? members : []).map(member => [member.id, member]))
      } catch (error) {
        console.error('Error loading members for the household strips:', error)
        this.membersById = null
      }
    },
    async loadPaidMonths() {
      try {
        this.paidByMember = paidMonthsFromMap(await api.getPaidMonths(12))
      } catch (error) {
        console.error('Error loading payments for the household strips:', error)
        this.paidByMember = null
      }
    },
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
      return this.$t('households.countMember', count)
    },
    // "35 years old", in the language of the page
    personAge(birthDate) {
      return ageText(birthDate, this.$t)
    },
    // the URL keeps page and size beside the chosen household
    openDetail(household) {
      this.$router.push({ query: { ...this.$route.query, id: String(household.id) } })
    },
    closeDetail() {
      this.$router.push({ query: this.queryWithoutId() })
    },
    queryWithoutId() {
      const { id: _id, ...rest } = this.$route.query
      return rest
    },
    // A later call (another household chosen) makes an earlier one that is still running stop
    async loadDetail() {
      const token = ++this.detailToken
      this.detail = null
      this.detailError = ''
      this.detailLoading = false
      if (!this.selectedId) return
      this.detailLoading = true
      try {
        const detail = await api.getHousehold(this.selectedId)
        if (token === this.detailToken) this.detail = detail
      } catch (error) {
        if (token !== this.detailToken) return
        console.error('Error loading household:', error)
        this.detailError = error.response?.status === 404
          ? this.$t('households.gone')
          : this.$t('households.didNotLoad')
      } finally {
        if (token === this.detailToken) this.detailLoading = false
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
      this.formOpen = true
    },
    validateForm() {
      this.formErrors = { ...EMPTY_ERRORS }
      if (!this.form.name.trim()) this.formErrors.name = this.$t('households.nameRequired')
      else if (this.form.name.trim().length > 100) this.formErrors.name = this.$t('households.nameTooLong')
      if (this.form.notes.length > 2000) this.formErrors.notes = this.$t('households.notesTooLong')
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
      if (!fieldErrors.length) rest.push(error.message || this.$t('common.requestFailed'))
      if (rest.length) {
        this.formError = rest.join(' ')
        this.notifyFailure(this.$t('households.couldNotSave'), error)
      }
    },
    async saveHousehold() {
      this.resetFormErrors()
      if (!this.validateForm()) return
      this.saving = true
      try {
        const request = Object.fromEntries(FIELDS.map(field => [field, this.form[field].trim()]))
        const saved = this.editing ? await api.updateHousehold(this.selected.id, request) : await api.createHousehold(request)
        await this.loadHouseholds()
        if (this.editing) await this.reloadDetail()
        else if (saved?.id) this.openDetail(saved)
        this.formOpen = false
        this.notify('success', this.editing ? this.$t('households.saved') : this.$t('households.added'), request.name)
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
        await this.$router.replace({ query: this.queryWithoutId() })
        this.ensureSelection()
        this.notify('success', this.$t('households.deleted'), name)
      } catch (error) {
        console.error('Error deleting household:', error)
        // the server says why (for example, who is still assigned): show it above the open household and in a toast
        this.deleteOpen = false
        this.detailError = error.message || this.$t('common.requestFailed')
        this.notifyFailure(this.$t('households.couldNotDelete'), error)
      } finally {
        this.deleting = false
      }
    },
    formatDay(date) {
      return formatDate(date, 'MMM d, yyyy')
    },
    // After a person changes: the counts in the list, the people in the open household and (a new member) the strips
    async reloadDetail() {
      try {
        this.detail = await api.getHousehold(this.selectedId)
      } catch (error) {
        console.error('Error reloading household:', error)
        this.detailError = error.response?.status === 404
          ? this.$t('households.gone')
          : this.$t('households.didNotRefresh')
      }
    },
    async reloadAfterPersonChange() {
      await Promise.all([this.loadHouseholds(), this.reloadDetail(), this.loadMembers()])
    },
    // The messages for the answers the user can act on; '' means "show the server's own text"
    personProblem(error, name) {
      const code = error.response?.data?.code
      if (code === 'PERSON_001') return this.$t('households.alreadyMember', { name })
      if (code === 'PERSON_002') return this.$t('households.hasMembership', { name })
      if (error.response?.status === 404) return this.$t('households.noLongerOnRegister', { name })
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
        this.notifyFailure(this.$t('households.couldNotOpenPerson'), problem || error)
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
      if (!form.name.trim()) errors.name = this.$t('households.personNameRequired')
      else if (form.name.trim().length > 100) errors.name = this.$t('households.nameTooLong')
      const email = form.email.trim()
      if (email && !isValidEmail(email)) errors.email = this.$t('households.emailInvalid')
      else if (email.length > 100) errors.email = this.$t('households.nameTooLong')
      if (!isValidPhone(form.phone)) errors.phone = this.$t('households.phoneInvalid')
      if (form.birthDate && form.birthDate >= localISODate()) errors.birthDate = this.$t('households.birthDatePast')
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
      if (!fieldErrors.length) rest.push(error.message || this.$t('common.requestFailed'))
      if (rest.length) {
        this.personFormError = rest.join(' ')
        this.notifyFailure(this.$t('households.couldNotSavePerson'), error)
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
        this.notify('success', editing ? this.$t('households.personSaved') : this.$t('households.personAdded'), request.name)
      } catch (error) {
        console.error('Error saving person:', error)
        const problem = editing ? this.personProblem(error, this.personTarget.name) : ''
        if (problem) {
          this.personFormOpen = false
          this.notifyFailure(this.$t('households.couldNotSavePerson'), problem)
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
        this.membershipErrors.joinDate = this.$t('households.joinDateFuture')
        return
      }
      this.membershipSaving = true
      const { id, name } = this.personTarget
      try {
        await api.startMembership(id, buildMembershipRequest(this.membershipForm))
        // the person leaves this list now and appears in Members on its next load
        await this.reloadAfterPersonChange()
        this.membershipOpen = false
        this.notify('success', this.$t('households.madeAMember'), name)
      } catch (error) {
        console.error('Error starting membership:', error)
        const problem = this.personProblem(error, name)
        if (problem) {
          this.membershipOpen = false
          this.notifyFailure(this.$t('households.couldNotMakeMember'), problem)
          await this.reloadAfterPersonChange()
          return
        }
        const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
        const rest = []
        for (const { field, message } of fieldErrors) {
          if (field in EMPTY_MEMBERSHIP_ERRORS && !this.membershipErrors[field]) this.membershipErrors[field] = message
          else rest.push(message)
        }
        if (!fieldErrors.length) rest.push(error.message || this.$t('common.requestFailed'))
        if (rest.length) {
          this.membershipError = rest.join(' ')
          this.notifyFailure(this.$t('households.couldNotMakeMember'), error)
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
        this.notify('success', this.$t('households.personDeleted'), name)
      } catch (error) {
        console.error('Error deleting person:', error)
        // a person who became a member meanwhile (409), or one already gone (404): say so and refresh the list
        const problem = this.personProblem(error, name)
        this.personDeleteOpen = false
        this.detailError = problem || error.message || this.$t('common.requestFailed')
        this.notifyFailure(this.$t('households.couldNotDeletePerson'), problem || error)
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
      this.notify('error', title, typeof error === 'string' ? error : error.message || this.$t('common.requestFailed'))
    }
  }
}
</script>

<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead title="Payments" lead="Record what members paid and see the history." band>
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportPayments">
          <Icon name="download" :size="16" class="mr-1.5" />Export CSV
        </BaseButton>
        <BaseButton @click="openRecord">
          <Icon name="plus" :size="16" class="mr-1.5" />Record payment
        </BaseButton>
      </template>
    </PageHead>

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The payments did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadAll">Try again</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading payments...</p>

    <!-- Figures from GET /payments/summary (the server adds them up, the browser never holds every payment) -->
    <dl v-if="summary && summary.count" class="m-0 mb-6 grid grid-cols-2 gap-2 lg:grid-cols-3">
      <StatTile v-for="figure in figures" :key="figure.label" slim :label="figure.label" :value="figure.value" />
    </dl>

    <!-- The last twelve months, the same chart as the Overview -->
    <CollectedChart v-if="anyPayments" ref="chart" class="mb-6" />

    <!-- Filters -->
    <!-- From lg one row, labels visually hidden: search on the left, Method on the right -->
    <form v-if="showFilters" class="mb-6 grid grid-cols-2 gap-3 md:flex md:flex-wrap md:items-end lg:mb-4 lg:items-center lg:gap-x-4" role="search" aria-label="Filter payments" @submit.prevent="commitSearch">
      <div class="col-span-2 md:col-span-1 md:min-w-60 md:flex-1 lg:max-w-60 lg:flex-[1_1_10rem]">
        <label for="filter-search" :class="[LABEL, 'lg:sr-only']">Search</label>
        <input id="filter-search" v-model="searchText" type="search" placeholder="Search member name" autocomplete="off" :class="[CONTROL, 'lg:text-sm']">
      </div>
      <div class="col-span-2 md:col-span-1 md:w-48 lg:order-3 lg:ml-auto lg:w-44">
        <label for="filter-method" :class="[LABEL, 'lg:sr-only']">Method</label>
        <select id="filter-method" v-model="method" :class="[CONTROL, 'lg:text-sm']">
          <option value="">All methods</option>
          <option v-for="method in PAYMENT_METHODS" :key="method.value" :value="method.value">{{ method.label }}</option>
        </select>
      </div>
      <TextButton v-if="(hasActiveFilters || searchText) && payments.length" class="col-span-2 text-left md:col-span-1 md:py-1.5 lg:order-2" @click="clearFilters">Clear filters</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !total && !hasActiveFilters && !searchText.trim()">
      <EmptyNote>No payments yet. Record the first one.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="openRecord">Record payment</BaseButton>
    </div>
    <div v-else-if="loaded && !loadError && !total">
      <EmptyNote>No payments match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <!-- While another page loads the rows on screen stay, dimmed, until the new ones arrive -->
    <p v-if="loading && loaded" class="sr-only" role="status">Updating payments...</p>
    <div v-if="payments.length" :class="loading ? 'opacity-60' : ''" :aria-busy="loading ? 'true' : undefined">
      <!-- from lg the visible count is the table card's footer; this line stays for screen readers -->
      <p class="mt-0 mb-2 text-sm text-muted lg:sr-only" aria-live="polite">{{ countText }}</p>

      <!-- md to lg: ruled table -->
      <table :class="[TABLE, 'lg:hidden']">
        <caption class="sr-only">Payment history. Sorted by {{ sortText }}</caption>
        <thead>
          <tr class="border-b border-rule">
            <th scope="col" :aria-sort="sortState('paymentDate')" :class="TH"><SortButton label="Paid on" :state="sortState('paymentDate')" @click="setSort('paymentDate')" /></th>
            <th scope="col" :aria-sort="sortState('member')" :class="TH"><SortButton label="Member" :state="sortState('member')" @click="setSort('member')" /></th>
            <th scope="col" :aria-sort="sortState('period')" :class="TH"><SortButton label="Month covered" :state="sortState('period')" @click="setSort('period')" /></th>
            <th scope="col" :class="TH">Method</th>
            <th scope="col" :aria-sort="sortState('amount')" :class="[TH, 'text-right']"><SortButton label="Amount" :state="sortState('amount')" @click="setSort('amount')" /></th>
            <th scope="col" :class="TH">Receipt</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="payment in payments" :key="payment.id" class="h-(--row-h) border-b border-rule">
            <td :class="[TD, 'whitespace-nowrap']">{{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</td>
            <td :class="[TD, 'max-w-0 w-[30%] font-medium [overflow-wrap:anywhere]']">{{ payment.member?.name || 'Unknown' }}</td>
            <td :class="[TD, 'whitespace-nowrap']">{{ periodLabel(payment.period) }}</td>
            <td :class="[TD, 'whitespace-nowrap']">{{ methodLabel(payment.paymentMethod) }}</td>
            <td :class="[TD, 'text-right whitespace-nowrap']">{{ formatMoney(payment.amount) }}</td>
            <td :class="TD">
              <TextButton class="py-1" @click="openReceipt(payment)">
                {{ receiptNumber(payment) }}<span class="sr-only">, receipt for {{ payment.member?.name || 'Unknown' }}</span>
              </TextButton>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- lg and up: the table in a bordered card (grey header row, hairline between rows, footer line); it scrolls sideways inside the card -->
      <div class="hidden overflow-x-auto rounded-md border border-rule bg-paper lg:block">
        <div class="min-w-[44rem]">
          <table :class="TABLE_FROM_LG">
            <caption class="sr-only">Payment history. Sorted by {{ sortText }}</caption>
            <thead>
              <tr class="border-b border-rule">
                <th scope="col" :class="CARD_TH">Receipt</th>
                <th scope="col" :aria-sort="sortState('paymentDate')" :class="CARD_TH"><SortButton label="Paid on" :state="sortState('paymentDate')" @click="setSort('paymentDate')" /></th>
                <th scope="col" :aria-sort="sortState('member')" :class="CARD_TH"><SortButton label="Member" :state="sortState('member')" @click="setSort('member')" /></th>
                <th scope="col" :aria-sort="sortState('period')" :class="CARD_TH"><SortButton label="Month" :state="sortState('period')" @click="setSort('period')" /></th>
                <th scope="col" :class="CARD_TH">Method</th>
                <th scope="col" :aria-sort="sortState('amount')" :class="[CARD_TH, 'text-right']"><SortButton label="Amount" :state="sortState('amount')" @click="setSort('amount')" /></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="payment in payments" :key="payment.id" class="h-(--row-h) border-b border-rule">
                <td :class="[CARD_TD, 'whitespace-nowrap']">
                  <TextButton class="py-1" @click="openReceipt(payment)">
                    {{ receiptNumber(payment) }}<span class="sr-only">, receipt for {{ payment.member?.name || 'Unknown' }}</span>
                  </TextButton>
                </td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">{{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</td>
                <td :class="[CARD_TD, 'max-w-0 w-[34%] font-medium [overflow-wrap:anywhere]']">{{ payment.member?.name || 'Unknown' }}</td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">{{ periodLabel(payment.period) }}</td>
                <td :class="[CARD_TD, 'whitespace-nowrap']">{{ methodLabel(payment.paymentMethod) }}</td>
                <td :class="[CARD_TD, 'text-right whitespace-nowrap']">{{ formatMoney(payment.amount) }}</td>
              </tr>
            </tbody>
          </table>
          <Pager v-bind="pagerProps" class="px-4 py-3" @update:page="setPage" @update:page-size="setPageSize" />
          <div :class="['flex flex-wrap justify-between gap-x-4 gap-y-2 px-4 text-sm text-muted', pagerShown ? 'pb-3' : 'py-3']">
            <span v-if="!pagerShown">{{ countText }}</span>
            <span class="ml-auto">Sorted by {{ sortText }}</span>
          </div>
        </div>
      </div>

      <!-- Below md: the same rows, stacked -->
      <ul class="m-0 list-none border-t border-rule p-0 md:hidden">
        <li v-for="payment in payments" :key="payment.id" class="border-b border-rule py-3">
          <div class="flex items-baseline justify-between gap-3">
            <span class="min-w-0 font-medium [overflow-wrap:anywhere]">{{ payment.member?.name || 'Unknown' }}</span>
            <span class="shrink-0 font-medium tabular-nums">{{ formatMoney(payment.amount) }}</span>
          </div>
          <div class="text-sm text-muted">{{ periodLabel(payment.period) }} &middot; {{ methodLabel(payment.paymentMethod) }}</div>
          <div class="flex items-center justify-between gap-3 text-sm text-muted tabular-nums">
            <span>Paid on {{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</span>
            <TextButton class="text-base" @click="openReceipt(payment)">
              {{ receiptNumber(payment) }}<span class="sr-only">, receipt for {{ payment.member?.name || 'Unknown' }}</span>
            </TextButton>
          </div>
        </li>
      </ul>
      <!-- below lg; from lg the pager is the table card's footer -->
      <Pager v-bind="pagerProps" class="mt-4 lg:hidden" @update:page="setPage" @update:page-size="setPageSize" />
    </div>

    <!-- Record payment (STAFF and above) -->
    <BaseModal v-if="authStore.isStaff" v-model="recordOpen" title="Record payment" size="md" sheet>
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <AlertBanner v-if="membersError">The members did not load. Close this and try again.</AlertBanner>
      <p v-else-if="!membersLoaded" class="m-0 py-2 text-muted" role="status">Loading members...</p>
      <EmptyNote v-else-if="!activeMembers.length">There are no active members to record a payment for. Add or reactivate a member first.</EmptyNote>
      <form v-else id="payment-form" class="flex flex-col gap-4" novalidate @submit.prevent="recordPayment">
        <MemberPicker id="payment-member" v-model="form.memberId" label="Member" class="max-lg:min-h-12" :members="activeMembers" :paid-by-member="paidByMember" :current-month="currentPeriod" :error="formErrors.memberId" />
        <!-- Phone sheet only: what the chosen member owes, from the same year strip as the Members list -->
        <section v-if="selectedMember && paidByMember" :aria-label="`${selectedMember.name}, dues`" class="flex flex-col gap-2.5 rounded-lg border border-rule bg-paper px-4 py-3.5 lg:hidden">
          <div class="text-xl font-medium [overflow-wrap:anywhere]">{{ selectedMember.name }}</div>
          <YearStrip size="large" v-bind="stripProps(selectedMember)" />
          <p class="m-0 text-lg leading-snug">{{ owed.sentence }}</p>
        </section>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <BaseInput id="payment-period" v-model="form.period" label="Month covered" type="month" class="max-lg:min-h-12" :max="currentPeriod" :hint="periodHint" :error="formErrors.period" />
          <BaseInput id="payment-date" v-model="form.paymentDate" label="Paid on" type="date" class="max-lg:min-h-12" :max="today" hint="Change this when you enter an older payment." :error="formErrors.paymentDate" />
        </div>
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <BaseInput id="payment-amount" v-model="form.amount" label="Amount" type="number" class="max-lg:min-h-12" min="0.01" step="0.01" inputmode="decimal" :error="formErrors.amount" />
          <BaseSelect id="payment-method" v-model="form.paymentMethod" label="Payment method" class="max-lg:min-h-12" :error="formErrors.paymentMethod">
            <option v-for="method in PAYMENT_METHODS" :key="method.value" :value="method.value">{{ method.label }}</option>
          </BaseSelect>
        </div>
        <BaseTextarea id="payment-notes" v-model="form.notes" label="Notes (optional)" :max="NOTES_MAX" :rows="2" :error="formErrors.notes" />
      </form>
      <template #footer>
        <BaseButton variant="secondary" :disabled="saving" @click="recordOpen = false">Cancel</BaseButton>
        <BaseButton type="submit" form="payment-form" :disabled="saving || !activeMembers.length" :aria-busy="saving ? 'true' : undefined">
          {{ saving ? 'Recording...' : 'Record payment' }}
        </BaseButton>
      </template>
    </BaseModal>

    <!-- Receipt -->
    <ReceiptDialog v-model="receiptOpen" :payment="selectedPayment" />
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, formatMoney, localISODate } from '@/utils'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'
import { methodLabel, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'
import { ariaSort, filtersQuery, nextSort, pageParams, readFilters, sortLabel } from '@/utils/paymentQuery'
import { PAGE_SIZES } from '@/utils/paging'
import { queryPaging } from '@/utils/queryPaging'
import { countsForDues } from '@/utils/memberStatus'
import { longMonth, owedSummary } from '@/utils/dues'
import { nextMonthCovered } from '@/utils/nextMonthCovered'
import { paidMonthsFromMap, stripCells } from '@/utils/yearStrip'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import CollectedChart from '@/components/CollectedChart.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import MemberPicker from '@/components/MemberPicker.vue'
import Pager from '@/components/Pager.vue'
import SortButton from '@/components/SortButton.vue'
import StatTile from '@/components/StatTile.vue'
import PageHead from '@/components/PageHead.vue'
import ReceiptDialog from '@/components/ReceiptDialog.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'

import { CONTROL, LABEL, TABLE, TABLE_FROM_LG, TABLE_TH as TH, TABLE_TD as TD, TABLE_CARD_TH as CARD_TH, TABLE_CARD_TD as CARD_TD } from '@/ui/classes'

const NOTES_MAX = 500
const EMPTY_ERRORS = { memberId: '', period: '', paymentDate: '', amount: '', paymentMethod: '', notes: '' }
// the search box waits this long after the last key before it asks the server
const SEARCH_DELAY = 300

// Month and date default to now each time the dialog opens
const emptyForm = () => ({
  memberId: '',
  period: localISODate().slice(0, 7),
  paymentDate: localISODate(),
  amount: '',
  paymentMethod: 'CASH',
  notes: ''
})

export default {
  name: 'PaymentsView',
  // page and size (?page=2&size=50) live in the URL beside search, method and sort (pagingExtraQuery)
  mixins: [queryPaging()],
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, CollectedChart, EmptyNote, Icon, MemberPicker, PageHead, Pager, ReceiptDialog, SortButton, StatTile, TextButton, YearStrip },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore(),
      formatDate,
      formatMoney,
      methodLabel,
      periodLabel,
      receiptNumber,
      TABLE,
      TABLE_FROM_LG,
      CARD_TH,
      CARD_TD,
      LABEL,
      CONTROL,
      TH,
      TD,
      NOTES_MAX,
      PAYMENT_METHODS
    }
  },
  data() {
    const { search, method, sort } = readFilters(this.$route?.query)
    return {
      members: [],
      membersLoaded: false,
      membersError: false,
      // the one page on screen, and how many payments match the filters (the server's totalElements)
      payments: [],
      total: 0,
      // the figures above the history: {thisMonth, allTime, average, count}; null until loaded or when they failed
      summary: null,
      // memberId -> Set of paid months (the last 12), for the dialog's strip and the picker's badges; null until loaded
      paidByMember: null,
      loaded: false,
      loading: false,
      loadError: false,
      // the box holds what is typed, `search` what the list was asked for (the box after SEARCH_DELAY)
      searchText: search,
      search,
      method,
      sort,
      recordOpen: false,
      form: emptyForm(),
      formError: '',
      formErrors: { ...EMPTY_ERRORS },
      saving: false,
      today: localISODate(),
      selectedPayment: null,
      receiptOpen: false,
      // the amount the last chosen member's most recent payment put in the field; a different value there was typed by the user
      prefilledAmount: '',
      // the month the form last put in Month covered; a different value there was typed by the user
      autoPeriod: ''
    }
  },
  computed: {
    currentPeriod() {
      return this.today.slice(0, 7)
    },
    selectedMember() {
      return this.members.find(member => String(member.id) === this.form.memberId) || null
    },
    // The member's year strip cells and what they owe (the step 1 rule: yearStrip.js); nothing until the paid months are in
    owed() {
      const member = this.selectedMember
      return member && this.paidByMember ? owedSummary(stripCells(this.stripArgs(member))) : { oldest: '', sentence: '' }
    },
    periodHint() {
      return this.owed.oldest && this.owed.oldest !== this.currentPeriod && this.form.period === this.owed.oldest && this.form.period === this.autoPeriod ? `${longMonth(this.owed.oldest)}, the oldest month not paid.` : ''
    },
    activeMembers() {
      return this.members.filter(countsForDues).sort((a, b) => a.name.localeCompare(b.name))
    },
    hasActiveFilters() {
      return !!this.search || !!this.method
    },
    // the chart and the filters only make sense once there is a payment (or a filter that hides them all)
    anyPayments() {
      return this.summary ? this.summary.count > 0 : this.total > 0
    },
    showFilters() {
      return this.loaded && !this.loadError && (this.total > 0 || this.hasActiveFilters || !!this.searchText)
    },
    figures() {
      return [
        { label: 'This month', value: formatMoney(this.summary.thisMonth) },
        { label: 'All time', value: formatMoney(this.summary.allTime) },
        { label: 'Average payment', value: formatMoney(this.summary.average) }
      ]
    },
    // "32 payments", or "3 of 32 payments" when a filter hides some
    countText() {
      const noun = this.total === 1 && !this.hasActiveFilters ? 'payment' : 'payments'
      const all = this.summary?.count
      return this.hasActiveFilters && all !== undefined && all !== this.total ? `${this.total} of ${all} ${noun}` : `${this.total} ${noun}`
    },
    sortText() {
      return sortLabel(this.sort)
    },
    pagerProps() {
      return { page: this.page, pageSize: this.pageSize, total: this.total }
    },
    pagerShown() {
      return this.total > Math.min(...PAGE_SIZES)
    },
    // what the table is asked for: any change loads the page again, and only then
    requestKey() {
      return JSON.stringify(pageParams({ page: this.page, size: this.pageSize, search: this.search, method: this.method, sort: this.sort }))
    }
  },
  watch: {
    // The month covered starts at the oldest month the member has not paid and the amount at what
    // that member paid last. Only choosing a member sets them, so a month typed afterwards stays.
    // A member with no unpaid month (or none chosen) puts the month back to the current one, but only
    // when the field still holds what this set: a typed month is never replaced.
    'form.memberId'(memberId) {
      this.applyOwedPeriod()
      this.prefillAmount(memberId)
    },
    // the paid months arrive after a member was chosen (the dialog opened from ?memberId=): the same rule again
    paidByMember() {
      if (this.form.memberId) this.applyOwedPeriod()
    },
    requestKey() {
      this.loadPage()
    },
    searchText() {
      clearTimeout(this.searchTimer)
      this.searchTimer = setTimeout(() => this.commitSearch(), SEARCH_DELAY)
    },
    method() {
      this.resetPage()
    },
    // a link to /payments?search= (a member's page) while this screen is already open, or a bare /payments
    '$route.query.search'() {
      this.readFiltersFromRoute()
    },
    '$route.query.method'() {
      this.readFiltersFromRoute()
    },
    '$route.query.sort'() {
      this.readFiltersFromRoute()
    }
  },
  async created() {
    // counters of the newest request: an older answer than the last one asked for is dropped
    this.pageRequest = 0
    this.prefillRequest = 0
    // a member's page links here with their name to show only their payments (?search=, read in data())
    // Members are only needed to record a payment (STAFF and above); the history carries each member's name
    await Promise.all([this.loadPage(), this.loadSummary(), this.authStore.isStaff ? this.loadMembers() : null])
    this.openForQueryMember()
  },
  beforeUnmount() {
    clearTimeout(this.searchTimer)
  },
  methods: {
    stripArgs(member) {
      return {
        currentMonth: this.currentPeriod,
        joinDate: member.joinDate || '',
        paidMonths: this.paidByMember?.get(member.id) || new Set(),
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member)
      }
    },
    stripProps(member) {
      return { ...this.stripArgs(member), label: `Dues for ${member.name}, last 12 months` }
    },
    // search, method and sort ride in the URL beside page and size (left out when they are the defaults)
    pagingExtraQuery() {
      return filtersQuery({ search: this.search, method: this.method, sort: this.sort })
    },
    sortState(field) {
      return ariaSort(this.sort, field)
    },
    setSort(field) {
      this.sort = nextSort(this.sort, field)
      this.resetPage()
    },
    // The box's text becomes the search: after SEARCH_DELAY, or at once on Enter. The text is trimmed; a change goes back to page 1.
    commitSearch() {
      clearTimeout(this.searchTimer)
      const text = this.searchText.trim()
      if (text === this.search) return
      this.search = text
      this.resetPage()
    },
    clearFilters() {
      this.searchText = ''
      this.method = ''
      this.commitSearch()
    },
    // The URL changed from outside (a link, not our own write): take its search, method and sort
    readFiltersFromRoute() {
      const { search, method, sort } = readFilters(this.$route?.query)
      if (search !== this.search) {
        this.searchText = search
        this.commitSearch()
      }
      if (method !== this.method) this.method = method
      if (sort.field !== this.sort.field || sort.direction !== this.sort.direction) this.sort = sort
    },
    // One page of the history. Only the newest request may change the screen: a slower, older answer is dropped.
    async loadPage() {
      const request = ++this.pageRequest
      this.loading = true
      try {
        const data = await api.getPaymentsPage(pageParams({ page: this.page, size: this.pageSize, search: this.search, method: this.method, sort: this.sort }))
        if (request !== this.pageRequest) return
        this.payments = Array.isArray(data?.content) ? data.content : []
        this.total = Number(data?.totalElements) || 0
        this.loadError = false
        // a page past the last (an old link) becomes the last, which loads again
        this.settlePage(this.total)
      } catch (error) {
        if (request !== this.pageRequest) return
        console.error('Error loading payments:', error)
        this.payments = []
        this.loadError = true
      } finally {
        if (request === this.pageRequest) {
          this.loading = false
          this.loaded = true
        }
      }
    },
    // The three figures; a failure only hides them
    async loadSummary() {
      try {
        this.summary = await api.getPaymentSummary()
      } catch (error) {
        console.error('Error loading the payment figures:', error)
        this.summary = null
      }
    },
    loadAll() {
      return Promise.all([this.loadPage(), this.loadSummary()])
    },
    async loadMembers() {
      try {
        const members = await api.getMembers()
        this.members = Array.isArray(members) ? members : []
        this.membersError = false
      } catch (error) {
        console.error('Error loading members:', error)
        this.membersError = true
      } finally {
        this.membersLoaded = true
      }
    },
    // Who paid which of the last 12 months: the dialog's strip and the picker's badges. A failure only hides them.
    async loadPaidMonths() {
      try {
        this.paidByMember = paidMonthsFromMap(await api.getPaidMonths(12))
      } catch (error) {
        console.error('Error loading the paid months:', error)
        this.paidByMember = null
      }
    },
    // /payments?memberId=<id> (the Members phone card) opens the dialog with that member chosen (STAFF and above);
    // the parameter is dropped so a reload or a close does not bring the dialog back
    openForQueryMember() {
      const memberId = this.$route?.query?.memberId
      if (memberId === undefined) return
      this.$router.replace({ query: { ...this.$route.query, memberId: undefined } })
      if (!this.authStore.isStaff || !this.membersLoaded || this.membersError) return
      this.openRecord()
      if (this.activeMembers.some(member => String(member.id) === String(memberId))) this.form.memberId = String(memberId)
    },
    applyOwedPeriod() {
      const next = nextMonthCovered(this.autoPeriod, this.form.period, this.owed.oldest, this.currentPeriod)
      this.form.period = next.period
      this.autoPeriod = next.auto
    },
    // Only an empty Amount, or one this method filled, is replaced: what the user typed stays. The member's newest payment
    // comes from their own list (GET /payments/member/{id}); a newer choice, or typing meanwhile, drops the answer.
    async prefillAmount(memberId) {
      const replaceable = () => this.form.amount === '' || this.form.amount === this.prefilledAmount
      if (!replaceable()) return
      const request = ++this.prefillRequest
      let last = null
      if (memberId) {
        try {
          const payments = await api.getPaymentsByMember(memberId)
          last = sortPayments(Array.isArray(payments) ? payments : [])[0] || null
        } catch (error) {
          console.error('Error loading the member payments for the amount:', error)
          return
        }
        if (request !== this.prefillRequest || !replaceable()) return
      }
      this.prefilledAmount = last ? String(last.amount) : ''
      this.form.amount = this.prefilledAmount
    },
    openRecord() {
      this.form = emptyForm()
      this.prefilledAmount = ''
      this.autoPeriod = this.form.period
      this.today = localISODate()
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
      this.recordOpen = true
      this.loadPaidMonths()
      if (this.membersError) this.loadMembers()
    },
    validateForm() {
      const errors = { ...EMPTY_ERRORS }
      const f = this.form
      if (!f.memberId) errors.memberId = 'Choose a member.'
      if (!f.period) errors.period = 'Choose the month this payment covers.'
      else if (f.period > this.currentPeriod) errors.period = 'Choose this month or an earlier one.'
      if (!f.paymentDate) errors.paymentDate = 'Choose the day the payment was made.'
      else if (f.paymentDate > this.today) errors.paymentDate = 'The payment date cannot be in the future.'
      if (!(Number(f.amount) >= 0.01)) errors.amount = 'Enter an amount of at least 0.01.'
      if (f.notes.length > NOTES_MAX) errors.notes = `Notes can be up to ${NOTES_MAX} characters.`
      this.formErrors = errors
      return !Object.values(errors).some(Boolean)
    },
    // Each server field error goes under its field; a plain 400 (duplicate month, inactive member,
    // period too far back) goes in the banner at the top of the dialog
    showSaveError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        if (field in EMPTY_ERRORS && !this.formErrors[field]) this.formErrors[field] = message
        else rest.push(message)
      }
      if (!fieldErrors.length) rest.push(error.message || 'The payment was not recorded. Try again.')
      if (rest.length) {
        this.formError = rest.join(' ')
        this.notifyFailure('Could not record payment', error)
      }
    },
    async recordPayment() {
      this.formError = ''
      if (!this.validateForm()) return
      this.saving = true
      try {
        const request = buildPaymentRequest(this.form)
        const member = this.members.find(m => m.id === request.memberId)
        await api.createPayment(request)
        this.recordOpen = false
        this.form = emptyForm()
        // the page, the figures and the chart; the members too, a payment resets their months-behind count
        await Promise.all([this.loadAll(), this.authStore.isStaff ? this.loadMembers() : null])
        this.$refs.chart?.load()
        this.notify('success', 'Payment recorded', `${member?.name || 'Member'}, ${periodLabel(request.period)}: ${formatMoney(request.amount)}`)
      } catch (error) {
        console.error('Error recording payment:', error)
        this.showSaveError(error)
      } finally {
        this.saving = false
      }
    },
    openReceipt(payment) {
      this.selectedPayment = payment
      this.receiptOpen = true
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || 'Request failed')
    },
    async exportPayments() {
      try {
        const response = await api.exportPayments()
        // api.request() already returns response.data (the blob)
        downloadBlob(response, `payments_${new Date().toISOString().split('T')[0]}.csv`)
      } catch (error) {
        console.error('Error exporting payments:', error)
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

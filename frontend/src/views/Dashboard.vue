<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead :title="$t('nav.overview')" :lead="$t('dashboard.lead')" band />

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      {{ $t('dashboard.loadError') }}
    </AlertBanner>
    <p v-else-if="!loaded" class="m-0 py-3 text-sm text-muted" role="status">{{ $t('dashboard.loading') }}</p>

    <template v-if="loaded">
      <!-- below lg four tiles; from lg one bordered card, its cells divided by hairlines (the gap shows the rule colour) -->
      <dl class="m-0 mb-6 grid grid-cols-2 gap-2 lg:flex lg:gap-px lg:overflow-hidden lg:rounded-md lg:border lg:border-rule lg:bg-rule">
        <StatTile slim cell :label="$t('dashboard.collectedIn', { month: monthName })" :value="formatMoney(stats.monthlyRevenue)" />
        <StatTile slim cell :label="$t('dues.badgePaid')" :value="paidCount" :hint="$t('dashboard.ofMembers', { n: activeCount })" tone="paid" />
        <StatTile slim cell :label="$t('dashboard.behindOnDues')" :value="behindMembers.length" :hint="behindHint" tone="behind" />
        <StatTile
          v-if="failedReminders !== null"
          slim
          cell
          :label="$t('dashboard.reminders')"
          :value="failedReminders"
          :hint="$t('dashboard.remindersFailed')"
          :tone="failedReminders > 0 ? 'danger' : 'default'"
          to="/communications"
        />
      </dl>

      <!--
        Every block is a card. Below xl they stack in one column, full width (the ledger needs the room at 1000px);
        from xl the ledger is the left column and Latest payments the right. The two column wrappers only exist
        from xl (display: contents before), so DOM order alone sets the reading order below it.
      -->
      <div class="flex flex-col gap-6 xl:grid xl:grid-cols-[minmax(0,1fr)_21rem] xl:items-start">
        <div class="contents min-w-0 xl:flex xl:flex-col xl:gap-6">
        <!-- The year ledger: who owes, most behind first, one square a month; behind rows carry the call actions -->
        <section :class="[CARD, 'min-w-0']" aria-labelledby="ledger-title">
          <div class="mb-3 flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
            <div>
              <SectionTitle id="ledger-title" class="!mb-0.5 !text-xl">{{ wide ? $t('dashboard.ledgerWideTitle') : $t('dashboard.ledgerNarrowTitle') }}</SectionTitle>
              <p class="m-0 max-w-[52ch] text-sm text-muted">
                {{ $t('dashboard.behindCaption', { range: stripRange }) }}
              </p>
            </div>
            <ul v-if="wide" class="m-0 flex list-none flex-wrap gap-x-4 gap-y-2 p-0 text-xs text-muted" aria-hidden="true">
              <li v-for="item in LEGEND" :key="item.labelKey" class="flex items-center gap-1.5">
                <span :class="['box-border block size-3.5 rounded-sm', SQUARES[item.state]]"></span>{{ $t(item.labelKey) }}
              </li>
            </ul>
          </div>
          <template v-if="ledgerRows.length">
            <div :class="wide ? 'overflow-x-auto' : ''">
            <div :class="wide ? 'min-w-[720px]' : ''">
            <div :class="[ledgerGrid, 'hidden border-b border-rule pb-2 text-xs font-medium text-muted sm:grid']" aria-hidden="true">
              <span>{{ $t('members.colMember') }}</span>
              <span v-if="wide" class="flex gap-1">
                <span v-for="name in monthLabels" :key="name" class="w-7 text-center text-[11px]">{{ name }}</span>
              </span>
              <span v-else>{{ stripRange }}</span>
              <span class="text-right">{{ $t('dashboard.behind') }}</span>
            </div>
            <ul class="m-0 list-none p-0">
              <li v-for="member in shownLedgerRows" :key="member.id" :class="[ledgerGrid, 'min-h-(--list-row-h) border-b border-rule py-2']">
                <span class="min-w-0 font-medium [overflow-wrap:anywhere]"><router-link :to="`/members/${member.id}`">{{ member.name }}</router-link></span>
                <span v-if="paidByMember" class="max-sm:order-3 max-sm:col-span-2"><YearStrip v-bind="stripProps(member)" :size="wide ? 'ledger' : 'compact'" /></span>
                <span v-else class="text-muted max-sm:order-3"><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('members.monthsNotLoaded') }}</span></span>
                <span class="text-right max-sm:order-2">
                  <StatusLabel v-if="member.consecutiveMonthsMissed > 0" tone="behind">{{ monthsBehind(member.consecutiveMonthsMissed, $t) }}</StatusLabel>
                  <StatusLabel v-else tone="muted">{{ $t('dues.badgeDue') }}</StatusLabel>
                </span>
                <span
                  v-if="member.consecutiveMonthsMissed > 0 && (member.phone || authStore.isStaff)"
                  class="col-span-2 flex flex-wrap gap-2 max-sm:order-4 max-sm:pb-1 sm:col-span-3 sm:justify-end"
                >
                  <a v-if="member.phone" :href="telHref(member.phone)" :class="[CALL_ACTION, 'border-teal bg-teal text-paper hover:bg-teal-hover']">
                    <Icon name="phone" :size="14" />{{ $t('members.call') }}<span class="sr-only"> {{ member.name }}</span>
                  </a>
                  <router-link v-if="authStore.isStaff" to="/communications" :class="[CALL_ACTION, 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']">
                    {{ $t('members.sendReminder') }}<span class="sr-only">{{ $t('members.sendReminderSr', { name: member.name }) }}</span>
                  </router-link>
                </span>
              </li>
            </ul>
            <div v-if="wide && paidTotals" :class="[ledgerGrid, 'pt-2.5 text-xs font-medium']">
              <span class="text-muted">{{ $t('dashboard.membersWhoPaid') }}</span>
              <span class="flex gap-1">
                <span v-for="total in paidTotals" :key="total.month" class="w-7 text-center text-ink">{{ total.count }}<span class="sr-only">{{ $t('dashboard.membersPaidIn', { month: total.name }) }}</span></span>
              </span>
              <span></span>
            </div>
            </div>
            </div>
            <p v-if="ledgerRows.length > LEDGER_LIMIT" class="mt-3 mb-0 text-sm text-muted">
              {{ $t('dashboard.showingOf', { shown: LEDGER_LIMIT, total: ledgerRows.length }) }} <router-link to="/members?dues=behind">{{ $t('dashboard.seeAll') }}<span class="sr-only">{{ $t('dashboard.seeAllSr') }}</span></router-link>
            </p>
          </template>
          <EmptyNote v-else-if="activeCount > 0">{{ $t('dashboard.noOverdue') }}</EmptyNote>
          <EmptyNote v-else>{{ $t('dashboard.noActiveMembers') }}</EmptyNote>
        </section>
        </div>

        <div class="contents min-w-0 xl:flex xl:flex-col xl:gap-6">
          <section :class="[CARD, 'min-w-0']" aria-labelledby="payments-title">
            <SectionTitle id="payments-title" class="!mb-0.5 !text-xl">{{ $t('dashboard.latestPayments') }}</SectionTitle>
            <ul v-if="recentPayments.length" class="m-0 mt-2 list-none p-0">
              <li v-for="payment in recentPayments" :key="payment.id" :class="[ROW, 'items-baseline justify-between']">
                <div class="min-w-0">
                  <div class="font-medium [overflow-wrap:anywhere]">{{ payment.member?.name || $t('payments.unknown') }}</div>
                  <div class="text-xs text-muted">{{ paymentDetail(payment) }}</div>
                </div>
                <span class="shrink-0 font-medium tabular-nums">{{ formatMoney(payment.amount) }}</span>
              </li>
            </ul>
            <EmptyNote v-else class="mt-2">{{ $t('dashboard.noPayments') }}</EmptyNote>
            <p v-if="recentPayments.length" class="mt-3 mb-0 text-sm"><router-link to="/payments">{{ $t('dashboard.allPayments') }}</router-link></p>
          </section>
        </div>
      </div>
    </template>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import PageHead from '@/components/PageHead.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatTile from '@/components/StatTile.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import YearStrip from '@/components/YearStrip.vue'
import { useAuthStore } from '../stores/authStore'
import { formatDate as formatPaidDate, formatMoney, localISODate } from '@/utils'
import { methodKey, periodLabel } from '@/utils/paymentHistory'
import { CARD } from '@/ui/classes'
import { monthsBehind } from '@/utils/dues'
import { countsForDues } from '@/utils/memberStatus'
import { monthAndYear, paidMonthsFromMap, SQUARES, stripMonthLabels, stripMonths, stripRange } from '@/utils/yearStrip'

// One row of the ledger: member, the compact strip (12 squares of 10px, 2px apart), months behind
const LEDGER_GRID = 'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1.5 sm:grid-cols-[minmax(0,1fr)_142px_9.5rem]'
// The same row for the large-square strip (12 squares of 28px, 4px apart = 380px), Overview ledger on wide screens
const LEDGER_GRID_WIDE = 'grid grid-cols-[minmax(0,1fr)_380px_8.5rem] items-center gap-x-3 gap-y-1.5'
// Legend swatches: Paid, Missed, Due now, Not a member (one square each, drawn by the strip itself)
const LEGEND = [
  { state: 'paid', labelKey: 'strip.paid' },
  { state: 'missed', labelKey: 'strip.missed' },
  { state: 'due', labelKey: 'dashboard.legendDue' },
  { state: 'none', labelKey: 'dashboard.legendNotMember' }
]
// The large grid needs about 720px for the ledger card; that fits from the 2xl breakpoint (1400px) up
const WIDE_QUERY = '(min-width: 87.5rem)'
// The ledger lists the most behind members and stops here; Members holds the whole list
const LEDGER_LIMIT = 10
// how many payments "Latest payments" lists
const RECENT_PAYMENTS = 10
// One row in a card's list: Latest payments and Recent activity
const ROW = 'flex min-h-(--list-row-h) gap-3 border-b border-rule py-2.5 last:border-b-0 tabular-nums'
// A small action link in the Call this week panel
const CALL_ACTION = 'inline-flex min-h-8 items-center gap-1.5 rounded-sm border px-3 text-base font-medium no-underline'

export default {
  name: 'DashboardView',
  components: { AlertBanner, EmptyNote, Icon, PageHead, SectionTitle, StatTile, StatusLabel, YearStrip },
  setup() {
    return {
      authStore: useAuthStore(),
      monthsBehind,
      formatMoney,
      LEDGER_GRID,
      LEDGER_LIMIT,
      LEGEND,
      SQUARES,
      CALL_ACTION,
      CARD,
      ROW
    }
  },
  data() {
    return {
      loaded: false,
      loadError: false,
      stats: {
        totalMembers: 0,
        activeMembers: 0,
        overdueMembers: 0,
        monthlyRevenue: 0
      },
      recentPayments: [],
      members: [],
      paidByMember: null,
      today: localISODate(),
      failedReminders: null,
      wide: false,
      wideQuery: null
    }
  },
  computed: {
    activeCount() {
      return Number(this.stats.activeMembers) || 0
    },
    currentMonth() {
      return this.today.slice(0, 7)
    },
    monthName() {
      return formatPaidDate(`${this.currentMonth}-01`, 'MMMM')
    },
    // "5 members, 12 months unpaid": the server's per-member counts added up
    behindHint() {
      const members = this.behindMembers.length
      const months = this.behindMembers.reduce((sum, member) => sum + member.consecutiveMonthsMissed, 0)
      return this.$t('dashboard.behindHint', {
        members: this.$t('members.countMember', members),
        months: this.$t('dashboard.countMonth', months)
      })
    },
    ledgerGrid() {
      return this.wide ? LEDGER_GRID_WIDE : LEDGER_GRID
    },
    monthLabels() {
      return stripMonthLabels(this.currentMonth)
    },
    // How many dues-paying members have a payment for each of the twelve months; null when the payments did not load
    paidTotals() {
      if (!this.paidByMember) return null
      const payers = this.members.filter(countsForDues)
      return stripMonths(this.currentMonth).map((month, i) => ({
        month,
        name: monthAndYear(month),
        count: payers.filter(member => this.paidMonths(member).has(month)).length
      }))
    },
    stripRange() {
      return this.$t('strip.range', stripRange(this.currentMonth))
    },
    // Worst first: the longest-overdue members are the first calls to make
    behindMembers() {
      return this.members
        .filter(member => countsForDues(member) && member.consecutiveMonthsMissed > 0)
        .sort((a, b) => b.consecutiveMonthsMissed - a.consecutiveMonthsMissed)
    },
    // Everyone who owes: behind, or (when the payments loaded) not yet paid for the current month
    ledgerRows() {
      return this.members
        .filter(member => countsForDues(member) && (member.consecutiveMonthsMissed > 0 || (this.paidByMember && !this.paidMonths(member).has(this.currentMonth))))
        .sort((a, b) => (b.consecutiveMonthsMissed || 0) - (a.consecutiveMonthsMissed || 0) || a.name.localeCompare(b.name))
    },
    shownLedgerRows() {
      return this.ledgerRows.slice(0, LEDGER_LIMIT)
    },
    paidCount() {
      return Math.max(0, this.activeCount - this.behindMembers.length)
    }
  },
  async created() {
    await this.loadData()
  },
  mounted() {
    // jsdom and old browsers have no matchMedia: the compact strip stays
    if (typeof window.matchMedia !== 'function') return
    this.wideQuery = window.matchMedia(WIDE_QUERY)
    this.wide = this.wideQuery.matches
    this.wideQuery.addEventListener('change', this.onWideChange)
  },
  beforeUnmount() {
    this.wideQuery?.removeEventListener('change', this.onWideChange)
  },
  methods: {
    async loadData() {
      try {
        const [
          statsRes,
          paymentsRes,
          membersRes
        ] = await Promise.all([
          api.getDashboardStats(),
          api.getPaymentsPage({ page: 0, size: RECENT_PAYMENTS, sort: 'paymentDate,desc' }),
          api.getMembers()
        ])

        this.stats = statsRes
        this.recentPayments = Array.isArray(paymentsRes?.content) ? paymentsRes.content : []
        this.members = Array.isArray(membersRes) ? membersRes : []
        this.today = localISODate()
        this.loadError = false
        await Promise.all([this.loadPaidMonths(), this.loadFailedReminders()])
      } catch (error) {
        console.error('Error loading dashboard data:', error)
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    // The paid months behind the strips: GET /payments/paid-months, the call the Members screen makes too. A failure only hides the strips.
    async loadPaidMonths() {
      try {
        this.paidByMember = paidMonthsFromMap(await api.getPaidMonths(12))
      } catch (error) {
        console.error('Error loading payments for the year strip:', error)
        this.paidByMember = null
      }
    },
    // Reminder deliveries that failed, from the per-message summaries the Messages screen lists. A failure only hides the tile.
    async loadFailedReminders() {
      try {
        const messages = await api.getCommunications()
        this.failedReminders = (Array.isArray(messages) ? messages : [])
          .filter(message => message.type === 'REMINDER')
          .reduce((sum, message) => sum + (message.deliverySummary?.failed || 0), 0)
      } catch (error) {
        console.error('Error loading reminder deliveries:', error)
        this.failedReminders = null
      }
    },
    onWideChange(event) {
      this.wide = event.matches
    },
    paidMonths(member) {
      return this.paidByMember?.get(member.id) || new Set()
    },
    stripProps(member) {
      return {
        joinDate: member.joinDate || '',
        paidMonths: this.paidMonths(member),
        currentMonth: this.currentMonth,
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member),
        label: this.$t('strip.duesFor', { name: member.name })
      }
    },
    // "Oct 2026, Cash, paid Oct 4": the month it covers, how it was paid, the day it was paid
    paymentDetail(payment) {
      return [
        periodLabel(payment.period),
        this.$t(methodKey(payment.paymentMethod)),
        this.$t('dashboard.paidOnShort', { date: formatPaidDate(payment.paymentDate, 'MMM d') })
      ].filter(Boolean).join(this.$t('common.listSeparator'))
    },
    telHref(phone) {
      return `tel:${phone.replace(/[^+\d]/g, '')}`
    }
  }
}
</script>

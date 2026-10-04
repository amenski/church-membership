<template>
  <div>
    <PageHead title="Overview" lead="Who is behind on dues, and how this month is going." />

    <AlertBanner v-if="loadError">
      The overview did not load. Reload the page, or sign in again if it keeps happening.
    </AlertBanner>
    <p v-else-if="!loaded" class="m-0 py-3 text-sm text-muted" role="status">Loading overview...</p>

    <template v-if="loaded">
      <dl class="m-0 mb-6 grid grid-cols-2 gap-2 lg:grid-cols-4">
        <StatTile slim label="Active members" :value="activeCount" />
        <StatTile slim label="Paid up" :value="paidCount" :hint="`of ${activeCount}`" tone="paid" />
        <StatTile slim label="Behind" :value="behindMembers.length" tone="behind" />
        <StatTile slim label="This month" :value="formatMoney(stats.monthlyRevenue)" />
      </dl>

      <div class="mb-8 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_21rem] lg:items-start">
        <!-- The year ledger: who owes, most behind first, one square a month -->
        <section class="min-w-0 rounded-md border border-rule bg-paper p-(--card-pad)" aria-labelledby="ledger-title">
          <SectionTitle id="ledger-title" class="!mb-0.5 !text-xl">Dues by member</SectionTitle>
          <p class="m-0 mb-3 text-sm text-muted">
            {{ stripRange }}, one square a month. Members who are behind or due this month, most behind first.
          </p>
          <template v-if="ledgerRows.length">
            <div :class="[LEDGER_GRID, 'hidden border-b border-rule pb-2 text-xs font-medium text-muted sm:grid']" aria-hidden="true">
              <span>Member</span>
              <span>{{ stripRange }}</span>
              <span class="text-right">Behind</span>
            </div>
            <ul class="m-0 list-none p-0">
              <li v-for="member in ledgerRows" :key="member.id" :class="[LEDGER_GRID, 'min-h-(--list-row-h) border-b border-rule py-2']">
                <span class="min-w-0 font-medium [overflow-wrap:anywhere]">{{ member.name }}</span>
                <span v-if="paidByMember" class="max-sm:order-3 max-sm:col-span-2"><YearStrip v-bind="stripProps(member)" /></span>
                <span v-else class="text-muted max-sm:order-3"><span aria-hidden="true">&ndash;</span><span class="sr-only">Months paid did not load</span></span>
                <span class="text-right max-sm:order-2">
                  <StatusLabel v-if="member.consecutiveMonthsMissed > 0" tone="behind">{{ monthsBehind(member.consecutiveMonthsMissed) }}</StatusLabel>
                  <StatusLabel v-else tone="muted">Due this month</StatusLabel>
                </span>
              </li>
            </ul>
          </template>
          <EmptyNote v-else-if="activeCount > 0">No overdue members. Everyone is paid up for this month.</EmptyNote>
          <EmptyNote v-else>No active members yet. Add the first one under Members.</EmptyNote>
        </section>

        <aside class="flex min-w-0 flex-col gap-6">
          <section class="rounded-md border border-rule bg-paper p-(--card-pad)" aria-labelledby="calls-title">
            <SectionTitle id="calls-title" class="!mb-0.5 !text-xl">Call this week</SectionTitle>
            <p class="m-0 text-sm text-muted">The three members furthest behind.</p>
            <ul v-if="callList.length" class="m-0 mt-2 list-none p-0">
              <li v-for="member in callList" :key="member.id" class="border-b border-rule py-3 last:border-b-0 last:pb-0">
                <div class="flex items-baseline justify-between gap-3">
                  <span class="min-w-0 font-medium [overflow-wrap:anywhere]">{{ member.name }}</span>
                  <span class="shrink-0 text-sm font-medium text-ochre-text">{{ monthsBehind(member.consecutiveMonthsMissed) }}</span>
                </div>
                <div class="mt-2 flex flex-wrap gap-2">
                  <a v-if="member.phone" :href="telHref(member.phone)" :class="[CALL_ACTION, 'border-teal bg-teal text-paper hover:bg-teal-hover']">
                    <Icon name="phone" :size="14" />Call<span class="sr-only"> {{ member.name }}</span>
                  </a>
                  <router-link v-if="authStore.isStaff" to="/communications" :class="[CALL_ACTION, 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint']">
                    Send reminder<span class="sr-only"> to {{ member.name }}</span>
                  </router-link>
                </div>
              </li>
            </ul>
            <EmptyNote v-else class="mt-2">Nobody is behind. There is no one to call.</EmptyNote>
          </section>

          <section class="min-w-0" aria-labelledby="payments-title">
            <SectionTitle id="payments-title">Recent payments</SectionTitle>
            <RuledList v-if="recentPayments.length">
              <RuledRow v-for="payment in recentPayments" :key="payment.id">
                <span class="shrink-0 grow-0 basis-22 text-xs text-muted max-sm:basis-full">{{ formatDate(payment.paymentDate) }}</span>
                <span class="min-w-0 flex-auto [overflow-wrap:anywhere]">{{ payment.member?.name || 'Unknown' }}</span>
                <span class="text-right font-medium">{{ formatMoney(payment.amount) }}</span>
              </RuledRow>
            </RuledList>
            <EmptyNote v-else>No payments recorded yet. Record the first one under Payments.</EmptyNote>
          </section>
        </aside>
      </div>

      <div class="grid grid-cols-1 gap-8 lg:grid-cols-2">
        <section class="min-w-0" aria-labelledby="collected-title">
          <SectionTitle id="collected-title">Collected by month</SectionTitle>
          <template v-if="chartError">
            <AlertBanner>The monthly amounts did not load.</AlertBanner>
            <TextButton @click="loadCollected">Try again</TextButton>
          </template>
          <ul v-else-if="hasCollected" class="m-0 grid list-none gap-1.5 p-0">
            <li
              v-for="row in collectedRows"
              :key="row.month"
              class="grid grid-cols-[4.5rem_1fr_auto] items-center gap-x-3 text-xs sm:grid-cols-[5rem_1fr_9rem]"
            >
              <span class="text-muted">{{ row.label }}</span>
              <span class="block h-3" aria-hidden="true">
                <span
                  :class="['block h-full min-w-px rounded-r', row.current ? 'bg-teal-line' : 'bg-teal']"
                  :style="{ width: row.width }"
                ></span>
              </span>
              <span class="text-right tabular-nums text-ink">
                {{ formatMoney(row.amount) }}
                <span v-if="row.current" class="text-muted">in progress</span>
              </span>
            </li>
          </ul>
          <EmptyNote v-else>No payments in the last 12 months. Record one under Payments to see it here.</EmptyNote>
        </section>

        <section class="min-w-0" aria-labelledby="activity-title">
          <SectionTitle id="activity-title">Recent activity</SectionTitle>
          <RuledList v-if="activities.length">
            <RuledRow v-for="activity in activities" :key="activity.id">
              <span class="shrink-0 grow-0 basis-22 text-xs text-muted max-sm:basis-full">{{ formatDate(activity.date) }}</span>
              <span class="min-w-0 flex-auto [overflow-wrap:anywhere]">{{ activity.description }}</span>
            </RuledRow>
          </RuledList>
          <EmptyNote v-else>Nothing has happened yet. Payments and messages will show up here.</EmptyNote>
        </section>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import PageHead from '@/components/PageHead.vue'
import RuledList from '@/components/RuledList.vue'
import RuledRow from '@/components/RuledRow.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatTile from '@/components/StatTile.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'
import { useAuthStore } from '../stores/authStore'
import { formatMoney, localISODate } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { countsForDues } from '@/utils/memberStatus'
import { paidMonthsByMember, stripRangeLabel } from '@/utils/yearStrip'

// One row of the ledger: member, the compact strip (12 squares of 10px, 2px apart), months behind
const LEDGER_GRID = 'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1.5 sm:grid-cols-[minmax(0,1fr)_142px_9.5rem]'
// A small action link in the Call this week panel
const CALL_ACTION = 'inline-flex min-h-8 items-center gap-1.5 rounded-sm border px-3 text-base font-medium no-underline'

export default {
  name: 'DashboardView',
  components: { AlertBanner, EmptyNote, Icon, PageHead, RuledList, RuledRow, SectionTitle, StatTile, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      authStore: useAuthStore(),
      monthsBehind,
      formatMoney,
      LEDGER_GRID,
      CALL_ACTION
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
      collected: [],
      chartError: false,
      recentPayments: [],
      members: [],
      paidByMember: null,
      today: localISODate(),
      activities: []
    }
  },
  computed: {
    activeCount() {
      return Number(this.stats.activeMembers) || 0
    },
    currentMonth() {
      return this.today.slice(0, 7)
    },
    stripRange() {
      return stripRangeLabel(this.currentMonth)
    },
    // Worst first: the longest-overdue members are the first calls to make
    behindMembers() {
      return this.members
        .filter(member => countsForDues(member) && member.consecutiveMonthsMissed > 0)
        .sort((a, b) => b.consecutiveMonthsMissed - a.consecutiveMonthsMissed)
    },
    callList() {
      return this.behindMembers.slice(0, 3)
    },
    // Everyone who owes: behind, or (when the payments loaded) not yet paid for the current month
    ledgerRows() {
      return this.members
        .filter(member => countsForDues(member) && (member.consecutiveMonthsMissed > 0 || (this.paidByMember && !this.paidMonths(member).has(this.currentMonth))))
        .sort((a, b) => (b.consecutiveMonthsMissed || 0) - (a.consecutiveMonthsMissed || 0) || a.name.localeCompare(b.name))
    },
    hasCollected() {
      return this.collected.some(row => row.amount > 0)
    },
    // Oldest first, as the server sends it; the last row is the current month, still being collected
    collectedRows() {
      const max = Math.max(...this.collected.map(row => row.amount), 0)
      return this.collected.map((row, index) => ({
        ...row,
        label: this.monthLabel(row.month),
        current: index === this.collected.length - 1,
        width: max > 0 ? `${(row.amount / max) * 100}%` : '0%'
      }))
    },
    paidCount() {
      return Math.max(0, this.activeCount - this.behindMembers.length)
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    async loadData() {
      try {
        const [
          statsRes,
          paymentsRes,
          membersRes,
          activitiesRes
        ] = await Promise.all([
          api.getDashboardStats(),
          api.getRecentPayments(),
          api.getMembers(),
          api.getRecentActivities()
        ])

        this.stats = statsRes
        this.recentPayments = paymentsRes
        this.members = Array.isArray(membersRes) ? membersRes : []
        this.activities = activitiesRes
        this.today = localISODate()
        this.loadError = false
        await Promise.all([this.loadCollected(), this.loadPaidMonths()])
      } catch (error) {
        console.error('Error loading dashboard data:', error)
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    // The paid months behind the strips (the call the Members screen makes too). A failure only hides the strips.
    async loadPaidMonths() {
      try {
        this.paidByMember = paidMonthsByMember(await api.getPayments())
      } catch (error) {
        console.error('Error loading payments for the year strip:', error)
        this.paidByMember = null
      }
    },
    async loadCollected() {
      try {
        this.collected = await api.getCollectedByMonth()
        this.chartError = false
      } catch (error) {
        console.error('Error loading collected by month:', error)
        this.chartError = true
      }
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
        label: `Dues for ${member.name}, last 12 months`
      }
    },
    telHref(phone) {
      return `tel:${phone.replace(/[^+\d]/g, '')}`
    },
    monthLabel(month) {
      const [year, number] = month.split('-').map(Number)
      return new Date(year, number - 1, 1).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    }
  }
}
</script>

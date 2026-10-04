<template>
  <div>
    <PageHead title="Overview" lead="Who is behind on dues, and how this month is going." />

    <AlertBanner v-if="loadError">
      The overview did not load. Reload the page, or sign in again if it keeps happening.
    </AlertBanner>

    <!-- The thesis: who needs a call -->
    <section class="pb-6" aria-labelledby="hero-title">
      <SectionTitle id="hero-title">Who needs a call</SectionTitle>

      <template v-if="loaded">
        <template v-if="activeCount > 0">
          <p class="mt-0 mb-6 font-display text-3xl leading-[1.15] text-balance max-sm:text-2xl">
            <span :class="FIGURE">{{ paidCount }}</span> of
            <span :class="FIGURE">{{ activeCount }}</span> active members are paid up
          </p>
          <DuesMeter :total="activeCount" :paid="paidCount" />
        </template>
        <EmptyNote v-else>No active members yet. Add the first one under Members.</EmptyNote>

        <RuledList v-if="behindMembers.length" class="mt-6">
          <RuledRow v-for="member in behindMembers" :key="member.id">
            <span class="min-w-0 flex-auto [overflow-wrap:anywhere] max-sm:basis-full">{{ member.name }}</span>
            <StatusLabel tone="behind" class="min-w-42 max-sm:min-w-0 max-sm:flex-auto">{{ monthsBehind(member.consecutiveMonthsMissed) }}</StatusLabel>
            <TextButton
              v-if="authStore.isStaff"
              :disabled="remindingIds.includes(member.id)"
              @click="sendReminder(member)"
            >
              Send reminder
              <span class="sr-only">to {{ member.name }}</span>
            </TextButton>
          </RuledRow>
        </RuledList>
        <EmptyNote v-else-if="activeCount > 0">No overdue members. Everyone is paid up for this month.</EmptyNote>
      </template>
    </section>

    <!-- Quiet secondary figures -->
    <dl class="mt-6 mb-10 flex border-y border-rule py-4">
      <div class="flex-1 px-6 first:pl-0 not-first:border-l not-first:border-rule max-sm:px-3 max-sm:first:pl-0">
        <dt class="text-base font-medium text-muted">This month's payments</dt>
        <dd :class="[FIGURE, 'm-0 text-2xl leading-[1.3]']">{{ formatMoney(stats.monthlyRevenue) }}</dd>
      </div>
      <div class="flex-1 px-6 first:pl-0 not-first:border-l not-first:border-rule max-sm:px-3 max-sm:first:pl-0">
        <dt class="text-base font-medium text-muted">Active members</dt>
        <dd :class="[FIGURE, 'm-0 text-2xl leading-[1.3]']">{{ stats.activeMembers }}</dd>
      </div>
    </dl>

    <div class="grid grid-cols-1 gap-12 lg:grid-cols-2">
      <section class="min-w-0" aria-labelledby="payments-title">
        <SectionTitle id="payments-title">Recent payments</SectionTitle>
        <RuledList v-if="recentPayments.length">
          <RuledRow v-for="payment in recentPayments" :key="payment.id">
            <span class="shrink-0 grow-0 basis-22 text-base text-muted max-sm:basis-full">{{ formatDate(payment.paymentDate) }}</span>
            <span class="min-w-0 flex-auto [overflow-wrap:anywhere]">{{ payment.member?.name || 'Unknown' }}</span>
            <span class="text-right font-medium">{{ formatMoney(payment.amount) }}</span>
          </RuledRow>
        </RuledList>
        <EmptyNote v-else-if="loaded">No payments recorded yet. Record the first one under Payments.</EmptyNote>
      </section>

      <section class="min-w-0" aria-labelledby="activity-title">
        <SectionTitle id="activity-title">Recent activity</SectionTitle>
        <RuledList v-if="activities.length">
          <RuledRow v-for="activity in activities" :key="activity.id">
            <span class="shrink-0 grow-0 basis-22 text-base text-muted max-sm:basis-full">{{ formatDate(activity.date) }}</span>
            <span class="min-w-0 flex-auto [overflow-wrap:anywhere]">{{ activity.description }}</span>
          </RuledRow>
        </RuledList>
        <EmptyNote v-else-if="loaded">Nothing has happened yet. Payments and messages will show up here.</EmptyNote>
      </section>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import AlertBanner from '@/components/AlertBanner.vue'
import DuesMeter from '@/components/DuesMeter.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import RuledList from '@/components/RuledList.vue'
import RuledRow from '@/components/RuledRow.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { buildReminderRequest } from '@/utils/communicationPayload'
import { formatMoney } from '@/utils'
import { monthsBehind } from '@/utils/dashboardMeter'

// Big figures: Alegreya, tabular and lining so numbers line up
const FIGURE = 'font-display font-bold tabular-nums lining-nums'

export default {
  name: 'DashboardView',
  components: { AlertBanner, DuesMeter, EmptyNote, PageHead, RuledList, RuledRow, SectionTitle, StatusLabel, TextButton },
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      monthsBehind,
      formatMoney,
      FIGURE
    }
  },
  data() {
    return {
      loaded: false,
      loadError: false,
      remindingIds: [],
      stats: {
        totalMembers: 0,
        activeMembers: 0,
        overdueMembers: 0,
        monthlyRevenue: 0
      },
      recentPayments: [],
      overdueMembers: [],
      activities: []
    }
  },
  computed: {
    activeCount() {
      return Number(this.stats.activeMembers) || 0
    },
    // Worst first: the longest-overdue members are the first calls to make
    behindMembers() {
      return this.overdueMembers
        .filter(member => member.active)
        .sort((a, b) => b.consecutiveMonthsMissed - a.consecutiveMonthsMissed)
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
          overdueRes,
          activitiesRes
        ] = await Promise.all([
          api.getDashboardStats(),
          api.getRecentPayments(),
          api.getOverdueMembers(),
          api.getRecentActivities()
        ])

        this.stats = statsRes
        this.recentPayments = paymentsRes
        this.overdueMembers = overdueRes
        this.activities = activitiesRes
        this.loadError = false
      } catch (error) {
        console.error('Error loading dashboard data:', error)
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    formatDate(date) {
      return new Date(date).toLocaleDateString()
    },
    async sendReminder(member) {
      this.remindingIds.push(member.id)
      try {
        await api.sendToMember(member.id, buildReminderRequest(member))
        this.appStore.addNotification({
          type: 'success',
          title: 'Send reminder',
          message: `Reminder sent to ${member.name}`,
          isToast: true
        })
        await this.loadData()
      } catch (error) {
        this.appStore.addNotification({
          type: 'error',
          title: 'Send reminder',
          message: error.message || 'The reminder was not sent. Try again.',
          isToast: true
        })
      } finally {
        this.remindingIds = this.remindingIds.filter(id => id !== member.id)
      }
    }
  }
}
</script>

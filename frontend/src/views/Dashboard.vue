<template>
  <div>
    <PageHead title="Overview" lead="Who is behind on dues, and how this month is going." />

    <AlertBanner v-if="loadError">
      The overview did not load. Reload the page, or sign in again if it keeps happening.
    </AlertBanner>

    <!-- The thesis: who needs a call -->
    <section class="tw:pb-6" aria-labelledby="hero-title">
      <SectionTitle id="hero-title">Who needs a call</SectionTitle>

      <template v-if="loaded">
        <template v-if="activeCount > 0">
          <p class="tw:mt-0 tw:mb-6 tw:font-display tw:text-3xl tw:leading-[1.15] tw:text-balance tw:max-sm:text-2xl">
            <span :class="FIGURE">{{ paidCount }}</span> of
            <span :class="FIGURE">{{ activeCount }}</span> active members are paid up
          </p>
          <DuesMeter :total="activeCount" :paid="paidCount" />
        </template>
        <EmptyNote v-else>No active members yet. Add the first one under Members.</EmptyNote>

        <RuledList v-if="behindMembers.length" class="tw:mt-6">
          <RuledRow v-for="member in behindMembers" :key="member.id">
            <span class="tw:min-w-0 tw:flex-auto tw:[overflow-wrap:anywhere] tw:max-sm:basis-full">{{ member.name }}</span>
            <StatusLabel tone="behind" class="tw:min-w-42 tw:max-sm:min-w-0 tw:max-sm:flex-auto">{{ monthsBehind(member.consecutiveMonthsMissed) }}</StatusLabel>
            <TextButton
              v-if="authStore.isStaff"
              :disabled="remindingIds.includes(member.id)"
              @click="sendReminder(member)"
            >
              Send reminder
              <span class="tw:sr-only">to {{ member.name }}</span>
            </TextButton>
          </RuledRow>
        </RuledList>
        <EmptyNote v-else-if="activeCount > 0">No overdue members. Everyone is paid up for this month.</EmptyNote>
      </template>
    </section>

    <!-- Quiet secondary figures -->
    <dl class="tw:mt-6 tw:mb-10 tw:flex tw:border-y tw:border-rule tw:py-4">
      <div class="tw:flex-1 tw:px-6 tw:first:pl-0 tw:not-first:border-l tw:not-first:border-rule tw:max-sm:px-3 tw:max-sm:first:pl-0">
        <dt class="tw:text-base tw:font-medium tw:text-muted">This month's payments</dt>
        <dd :class="[FIGURE, 'tw:m-0 tw:text-2xl tw:leading-[1.3]']">{{ formatMoney(stats.monthlyRevenue) }}</dd>
      </div>
      <div class="tw:flex-1 tw:px-6 tw:first:pl-0 tw:not-first:border-l tw:not-first:border-rule tw:max-sm:px-3 tw:max-sm:first:pl-0">
        <dt class="tw:text-base tw:font-medium tw:text-muted">Active members</dt>
        <dd :class="[FIGURE, 'tw:m-0 tw:text-2xl tw:leading-[1.3]']">{{ stats.activeMembers }}</dd>
      </div>
    </dl>

    <div class="tw:grid tw:grid-cols-1 tw:gap-12 tw:lg:grid-cols-2">
      <section class="tw:min-w-0" aria-labelledby="payments-title">
        <SectionTitle id="payments-title">Recent payments</SectionTitle>
        <RuledList v-if="recentPayments.length">
          <RuledRow v-for="payment in recentPayments" :key="payment.id">
            <span class="tw:shrink-0 tw:grow-0 tw:basis-22 tw:text-base tw:text-muted tw:max-sm:basis-full">{{ formatDate(payment.paymentDate) }}</span>
            <span class="tw:min-w-0 tw:flex-auto tw:[overflow-wrap:anywhere]">{{ payment.member?.name || 'Unknown' }}</span>
            <span class="tw:text-right tw:font-medium">{{ formatMoney(payment.amount) }}</span>
          </RuledRow>
        </RuledList>
        <EmptyNote v-else-if="loaded">No payments recorded yet. Record the first one under Payments.</EmptyNote>
      </section>

      <section class="tw:min-w-0" aria-labelledby="activity-title">
        <SectionTitle id="activity-title">Recent activity</SectionTitle>
        <RuledList v-if="activities.length">
          <RuledRow v-for="activity in activities" :key="activity.id">
            <span class="tw:shrink-0 tw:grow-0 tw:basis-22 tw:text-base tw:text-muted tw:max-sm:basis-full">{{ formatDate(activity.date) }}</span>
            <span class="tw:min-w-0 tw:flex-auto tw:[overflow-wrap:anywhere]">{{ activity.description }}</span>
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
const FIGURE = 'tw:font-display tw:font-bold tw:tabular-nums tw:lining-nums'

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

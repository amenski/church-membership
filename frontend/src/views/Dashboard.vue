<template>
  <div>
    <PageHead title="Overview" lead="Who is behind on dues, and how this month is going." />

    <AlertBanner v-if="loadError">
      The overview did not load. Reload the page, or sign in again if it keeps happening.
    </AlertBanner>
    <p v-else-if="!loaded" class="m-0 py-3 text-sm text-muted" role="status">Loading overview...</p>

    <template v-if="loaded">
      <dl class="m-0 mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile label="Active members" :value="activeCount" />
        <StatTile label="Paid up" :value="paidCount" :hint="`of ${activeCount}`" tone="paid" />
        <StatTile label="Behind" :value="behindMembers.length" tone="behind" />
        <StatTile label="This month" :value="formatMoney(stats.monthlyRevenue)" />
      </dl>

      <section class="mb-8" aria-labelledby="behind-title">
        <SectionTitle id="behind-title">Needs a reminder</SectionTitle>
        <RuledList v-if="behindMembers.length">
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
        <EmptyNote v-else>No active members yet. Add the first one under Members.</EmptyNote>
      </section>

      <div class="grid grid-cols-1 gap-8 lg:grid-cols-2">
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
import PageHead from '@/components/PageHead.vue'
import RuledList from '@/components/RuledList.vue'
import RuledRow from '@/components/RuledRow.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatTile from '@/components/StatTile.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { buildReminderRequest } from '@/utils/communicationPayload'
import { formatMoney } from '@/utils'
import { monthsBehind } from '@/utils/dues'
import { countsForDues } from '@/utils/memberStatus'

export default {
  name: 'DashboardView',
  components: { AlertBanner, EmptyNote, PageHead, RuledList, RuledRow, SectionTitle, StatTile, StatusLabel, TextButton },
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      monthsBehind,
      formatMoney
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
        .filter(countsForDues)
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

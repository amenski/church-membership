<template>
  <div class="overview">
    <header class="page-head">
      <h1 class="page-title">Overview</h1>
      <p class="page-lead">Who is behind on dues, and how this month is going.</p>
    </header>

    <p v-if="loadError" class="alert alert-danger" role="alert">
      The overview did not load. Reload the page, or sign in again if it keeps happening.
    </p>

    <!-- The thesis: who needs a call -->
    <section class="hero" aria-labelledby="hero-title">
      <h2 id="hero-title" class="hero__title">Who needs a call</h2>

      <template v-if="loaded">
        <template v-if="activeCount > 0">
          <p class="hero__sentence">
            <span class="figure-display">{{ paidCount }}</span> of
            <span class="figure-display">{{ activeCount }}</span> active members are paid up
          </p>
          <DuesMeter :total="activeCount" :paid="paidCount" />
        </template>
        <p v-else class="empty-note">No active members yet. Add the first one under Members.</p>

        <ul v-if="behindMembers.length" class="ruled-list hero__list">
          <li v-for="member in behindMembers" :key="member.id" class="ruled-list__row">
            <span class="ruled-list__main">{{ member.name }}</span>
            <span class="status status--behind">{{ monthsBehind(member.consecutiveMonthsMissed) }}</span>
            <button
              v-if="authStore.isStaff"
              type="button"
              class="text-action"
              :disabled="remindingIds.includes(member.id)"
              @click="sendReminder(member)"
            >
              Send reminder
              <span class="visually-hidden">to {{ member.name }}</span>
            </button>
          </li>
        </ul>
        <p v-else-if="activeCount > 0" class="empty-note">No overdue members. Everyone is paid up for this month.</p>
        <p v-if="inactiveBehindCount" class="hero__note">
          {{ inactiveBehindCount }} inactive {{ inactiveBehindCount === 1 ? 'member is' : 'members are' }} not shown.
        </p>
      </template>
    </section>

    <!-- Quiet secondary figures -->
    <dl class="figures">
      <div class="figures__item">
        <dt>This month's payments</dt>
        <dd class="figure-display">{{ formatAmount(stats.monthlyRevenue) }}</dd>
      </div>
      <div class="figures__item">
        <dt>Active members</dt>
        <dd class="figure-display">{{ stats.activeMembers }}</dd>
      </div>
    </dl>

    <div class="row g-5">
      <section class="col-lg-6" aria-labelledby="payments-title">
        <h2 id="payments-title" class="section-title">Recent payments</h2>
        <ul v-if="recentPayments.length" class="ruled-list">
          <li v-for="payment in recentPayments" :key="payment.id" class="ruled-list__row">
            <span class="ruled-list__date">{{ formatDate(payment.paymentDate) }}</span>
            <span class="ruled-list__main">{{ payment.member?.name || 'Unknown' }}</span>
            <span class="ruled-list__amount">{{ formatAmount(payment.amount) }}</span>
          </li>
        </ul>
        <p v-else-if="loaded" class="empty-note">No payments recorded yet. Record the first one under Payments.</p>
      </section>

      <section class="col-lg-6" aria-labelledby="activity-title">
        <h2 id="activity-title" class="section-title">Recent activity</h2>
        <ul v-if="activities.length" class="ruled-list">
          <li v-for="activity in activities" :key="activity.id" class="ruled-list__row">
            <span class="ruled-list__date">{{ formatDate(activity.date) }}</span>
            <span class="ruled-list__main">{{ activity.description }}</span>
          </li>
        </ul>
        <p v-else-if="loaded" class="empty-note">Nothing has happened yet. Payments and messages will show up here.</p>
      </section>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import DuesMeter from '@/components/DuesMeter.vue'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { buildReminderRequest } from '@/utils/communicationPayload'
import { monthsBehind } from '@/utils/dashboardMeter'

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export default {
  name: 'DashboardView',
  components: { DuesMeter },
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      monthsBehind
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
    inactiveBehindCount() {
      return this.overdueMembers.filter(member => !member.active).length
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
    formatAmount(amount) {
      return currency.format(Number(amount) || 0)
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

<style scoped>
.hero {
  padding-bottom: var(--space-5);
}
.hero__title {
  font-size: 1.375rem;
  margin-bottom: var(--space-3);
}
.hero__sentence {
  font-family: var(--felege-font-display);
  font-size: 2.5rem;
  line-height: 1.15;
  text-wrap: balance;
  margin-bottom: var(--space-5);
}
.hero__sentence .figure-display { font-size: 2.5rem; }
.hero__list { margin-top: var(--space-5); }
.hero__list .status { min-width: 10.5rem; }
.hero__note {
  margin: var(--space-3) 0 0;
  font-size: 0.875rem;
  color: var(--felege-muted);
}

.figures {
  display: flex;
  margin: var(--space-5) 0 var(--space-6);
  padding: var(--space-4) 0;
  border-top: 1px solid var(--felege-rule);
  border-bottom: 1px solid var(--felege-rule);
}
.figures__item {
  flex: 1 1 0;
  padding: 0 var(--space-5);
}
.figures__item:first-child { padding-left: 0; }
.figures__item + .figures__item { border-left: 1px solid var(--felege-rule); }
.figures dt {
  font-size: 1rem;
  font-weight: 500;
  color: var(--felege-muted);
}
.figures dd {
  margin: 0;
  font-size: 1.75rem;
  line-height: 1.3;
}

@media (max-width: 575.98px) {
  .hero__list .ruled-list__main { flex-basis: 100%; }
  .hero__list .status { flex: 1 1 auto; min-width: 0; }
  .hero__sentence,
  .hero__sentence .figure-display { font-size: 1.75rem; }
  .figures__item { padding: 0 var(--space-3); }
}
</style>

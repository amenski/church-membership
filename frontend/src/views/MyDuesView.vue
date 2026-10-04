<template>
  <div class="mx-auto max-w-160">
    <p v-if="loading" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading your dues...</p>

    <AlertBanner v-else-if="error">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ error }}. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="load">Try again</BaseButton>
      </div>
    </AlertBanner>

    <section v-else-if="!dues" :class="CARD" aria-labelledby="none-title">
      <SectionTitle id="none-title">My dues</SectionTitle>
      <p class="mt-0 mb-4 text-(length:--text-body) leading-(--lh-body)">
        We could not find your membership. Ask the church office to check that your sign-in email matches the email they have for you.
      </p>
      <BaseButton variant="secondary" to="/profile" class="w-full sm:w-auto">Go to Profile</BaseButton>
    </section>

    <div v-else class="flex flex-col gap-5">
      <section aria-labelledby="my-dues-name">
        <h1 id="my-dues-name" class="m-0 text-2xl font-semibold text-ink [overflow-wrap:anywhere]">{{ dues.name }}</h1>
        <p v-if="since" class="mt-0.5 mb-0 text-sm text-muted">{{ since }}</p>
      </section>

      <section :class="CARD" aria-labelledby="my-dues-status">
        <h2 id="my-dues-status" class="m-0 text-lg font-semibold text-ink">{{ status.title }}</h2>
        <p v-if="status.detail" class="mt-1.5 mb-0 text-(length:--text-body) leading-(--lh-body)">{{ status.detail }}</p>
      </section>

      <section :class="CARD" aria-labelledby="my-dues-year">
        <h2 id="my-dues-year" class="m-0 mb-1 text-lg font-semibold text-ink">Your year</h2>
        <p class="mt-0 mb-3.5 text-sm text-muted">Each square is one month, {{ rangeLabel }}.</p>
        <YearStrip
          size="large"
          :current-month="currentMonth"
          :join-date="dues.joinDate || ''"
          :paid-months="paidMonths"
          :months-missed="dues.monthsBehind"
          :counts-for-dues="dues.status === 'MEMBER'"
          label="Your dues, last 12 months"
        />
      </section>

      <section :class="CARD" aria-labelledby="my-dues-receipts">
        <h2 id="my-dues-receipts" class="m-0 mb-1 text-lg font-semibold text-ink">Receipts</h2>
        <EmptyNote v-if="!dues.payments.length">No payments yet.</EmptyNote>
        <ul v-else class="m-0 list-none p-0">
          <li v-for="payment in dues.payments" :key="payment.receiptNumber" class="flex min-h-11 items-center justify-between gap-3 border-b border-rule py-3 last:border-b-0">
            <div class="min-w-0">
              <div class="text-(length:--text-body) font-medium">{{ longMonth(payment.period) }}</div>
              <div class="text-sm text-muted">{{ payment.receiptNumber }}, {{ methodLabel(payment.method) }}</div>
            </div>
            <div class="shrink-0 text-(length:--text-body) font-semibold tabular-nums">{{ formatMoney(payment.amount) }}</div>
          </li>
        </ul>
      </section>

      <section :class="[CARD, 'flex flex-col gap-3']" aria-labelledby="my-dues-details">
        <h2 id="my-dues-details" class="m-0 text-lg font-semibold text-ink">Your details</h2>
        <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-[0.9375rem]">
          <dt class="font-normal text-muted">Phone</dt>
          <dd class="m-0">{{ dues.phone || 'Not set' }}</dd>
          <dt class="font-normal text-muted">Email</dt>
          <dd class="m-0 min-w-0 [overflow-wrap:anywhere]">{{ dues.email || 'Not set' }}</dd>
          <dt class="font-normal text-muted">Household</dt>
          <dd class="m-0">{{ dues.householdName || 'None' }}</dd>
        </dl>
        <BaseButton to="/profile" class="w-full">Edit my details</BaseButton>
        <BaseButton variant="secondary" to="/profile" class="w-full">Change password</BaseButton>
        <button
          type="button"
          class="inline-flex min-h-12 w-full cursor-pointer items-center justify-center gap-2 rounded-sm border border-clay bg-paper text-lg font-medium text-clay hover:bg-clay-tint"
          @click="signOut"
        >
          <Icon name="log-out" :size="18" />Sign out
        </button>
      </section>
    </div>
  </div>
</template>

<script>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore, useAuthStore } from '@/stores/index.js'
import apiService from '@/services/api.js'
import { formatMoney, localISODate } from '@/utils'
import { longMonth } from '@/utils/dues'
import { memberSince, myDuesStatus } from '@/utils/myDues'
import { methodLabel } from '@/utils/paymentHistory'
import { stripRangeLabel } from '@/utils/yearStrip'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import YearStrip from '@/components/YearStrip.vue'
import { CARD } from '@/ui/classes'

export default {
  name: 'MyDuesView',
  components: { AlertBanner, BaseButton, EmptyNote, Icon, SectionTitle, YearStrip },
  setup() {
    const authStore = useAuthStore()
    const appStore = useAppStore()
    const router = useRouter()
    const dues = ref(null)
    const loading = ref(true)
    const error = ref('')
    const currentMonth = localISODate().slice(0, 7)

    // 404 is an answer, not a failure: no single member has this sign-in email
    const load = async () => {
      loading.value = true
      error.value = ''
      try {
        dues.value = await apiService.getMyDues()
      } catch (err) {
        dues.value = null
        if (err.response?.status !== 404) error.value = 'Failed to load your dues'
      } finally {
        loading.value = false
      }
    }

    const signOut = async () => {
      try {
        await authStore.logout()
        appStore.addNotification({ type: 'success', title: 'Sign out', message: 'You have been successfully signed out', isToast: true })
        router.push('/login')
      } catch {
        appStore.addNotification({ type: 'error', title: 'Logout Failed', message: 'An error occurred while signing out', isToast: true })
      }
    }

    onMounted(load)

    return {
      CARD,
      dues,
      loading,
      error,
      currentMonth,
      load,
      signOut,
      formatMoney,
      longMonth,
      methodLabel,
      rangeLabel: stripRangeLabel(currentMonth),
      since: computed(() => memberSince(dues.value?.joinDate)),
      status: computed(() => (dues.value ? myDuesStatus(dues.value, currentMonth) : { title: '', detail: '' })),
      paidMonths: computed(() => new Set(dues.value?.paidMonths || []))
    }
  }
}
</script>

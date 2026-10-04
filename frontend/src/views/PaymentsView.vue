<template>
  <div>
    <PageHead title="Payments" lead="Record what members paid and see the history.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportPayments">
          <Icon name="download" :size="16" class="mr-1.5" />Export CSV
        </BaseButton>
        <BaseButton @click="openRecord">
          <Icon name="plus" :size="16" class="mr-1.5" />Record payment
        </BaseButton>
      </template>
    </PageHead>

    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The payments did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadData">Try again</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading payments...</p>

    <!-- Figures from the loaded payments -->
    <dl v-if="payments.length" class="m-0 mb-6 grid grid-cols-2 gap-3 lg:grid-cols-3">
      <StatTile v-for="figure in figures" :key="figure.label" :label="figure.label" :value="figure.value" />
    </dl>

    <!-- Filters -->
    <form v-if="payments.length" class="mb-6 grid grid-cols-2 gap-3 md:flex md:flex-wrap md:items-end" role="search" aria-label="Filter payments" @submit.prevent>
      <div class="col-span-2 md:col-span-1 md:min-w-60 md:flex-1">
        <label for="filter-search" :class="LABEL">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search member name" autocomplete="off" :class="CONTROL">
      </div>
      <div class="col-span-2 md:col-span-1 md:w-48">
        <label for="filter-method" :class="LABEL">Method</label>
        <select id="filter-method" v-model="filters.method" :class="CONTROL">
          <option value="ALL">All methods</option>
          <option v-for="method in PAYMENT_METHODS" :key="method.value" :value="method.value">{{ method.label }}</option>
        </select>
      </div>
      <TextButton v-if="hasActiveFilters && filteredPayments.length" class="col-span-2 text-left md:col-span-1 md:py-1.5" @click="clearFilters">Clear filters</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !payments.length">
      <EmptyNote>No payments yet. Record the first one.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="mt-2" @click="openRecord">Record payment</BaseButton>
    </div>
    <div v-else-if="payments.length && !filteredPayments.length">
      <EmptyNote>No payments match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <template v-if="filteredPayments.length">
      <p class="mt-0 mb-2 text-sm text-muted" aria-live="polite">
        {{ hasActiveFilters ? `${filteredPayments.length} of ${payments.length} payments` : `${payments.length} ${payments.length === 1 ? 'payment' : 'payments'}` }}
      </p>

      <!-- md and up: ruled table -->
      <table :class="TABLE">
        <caption class="sr-only">Payment history, newest first</caption>
        <thead>
          <tr class="border-b border-rule">
            <th scope="col" :class="TH">Paid on</th>
            <th scope="col" :class="TH">Member</th>
            <th scope="col" :class="TH">Month covered</th>
            <th scope="col" :class="TH">Method</th>
            <th scope="col" :class="[TH, 'text-right']">Amount</th>
            <th scope="col" :class="TH">Receipt</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="payment in filteredPayments" :key="payment.id" class="h-(--row-h) border-b border-rule">
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

      <!-- Below md: the same rows, stacked -->
      <ul class="m-0 list-none border-t border-rule p-0 md:hidden">
        <li v-for="payment in filteredPayments" :key="payment.id" class="border-b border-rule py-3">
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
    </template>

    <!-- Record payment (STAFF and above) -->
    <BaseModal v-if="authStore.isStaff" v-model="recordOpen" title="Record payment" size="md" sheet>
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <EmptyNote v-if="!activeMembers.length">There are no active members to record a payment for. Add or reactivate a member first.</EmptyNote>
      <form v-else id="payment-form" class="flex flex-col gap-4" novalidate @submit.prevent="recordPayment">
        <BaseSelect id="payment-member" v-model="form.memberId" label="Member" class="max-lg:min-h-12" :error="formErrors.memberId">
          <option value="" disabled>Choose a member</option>
          <option v-for="member in activeMembers" :key="member.id" :value="String(member.id)">{{ member.name }}</option>
        </BaseSelect>
        <!-- Phone sheet only: what the chosen member owes, from the same year strip as the Members list -->
        <section v-if="selectedMember" :aria-label="`${selectedMember.name}, dues`" class="flex flex-col gap-2.5 rounded-lg border border-rule bg-paper px-4 py-3.5 lg:hidden">
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
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, formatMoney, localISODate } from '@/utils'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'
import { filterPayments, methodLabel, paymentsSummary, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'
import { countsForDues } from '@/utils/memberStatus'
import { longMonth, owedSummary } from '@/utils/dues'
import { paidMonthsByMember, stripCells } from '@/utils/yearStrip'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import StatTile from '@/components/StatTile.vue'
import PageHead from '@/components/PageHead.vue'
import ReceiptDialog from '@/components/ReceiptDialog.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'

import { CONTROL, LABEL, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'

const NOTES_MAX = 500
const EMPTY_ERRORS = { memberId: '', period: '', paymentDate: '', amount: '', paymentMethod: '', notes: '' }
const EMPTY_FILTERS = { search: '', method: 'ALL' }
// Same breakpoint as the shell (lg): below it the dialog is a full-screen sheet
const WIDE = '(min-width: 62rem)'

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
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, EmptyNote, Icon, PageHead, ReceiptDialog, StatTile, TextButton, YearStrip },
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
      LABEL,
      CONTROL,
      TH,
      TD,
      NOTES_MAX,
      PAYMENT_METHODS
    }
  },
  data() {
    return {
      members: [],
      payments: [],
      loaded: false,
      loadError: false,
      filters: { ...EMPTY_FILTERS },
      recordOpen: false,
      form: emptyForm(),
      formError: '',
      formErrors: { ...EMPTY_ERRORS },
      saving: false,
      today: localISODate(),
      selectedPayment: null,
      receiptOpen: false,
      wide: true
    }
  },
  computed: {
    currentPeriod() {
      return this.today.slice(0, 7)
    },
    selectedMember() {
      return this.members.find(member => String(member.id) === this.form.memberId) || null
    },
    paidByMember() {
      return paidMonthsByMember(this.payments)
    },
    // The member's year strip cells and what they owe (the step 1 rule: yearStrip.js)
    owed() {
      const member = this.selectedMember
      return member ? owedSummary(stripCells(this.stripArgs(member))) : { oldest: '', sentence: '' }
    },
    periodHint() {
      return !this.wide && this.owed.oldest && this.form.period === this.owed.oldest ? `${longMonth(this.owed.oldest)}, the oldest month not paid.` : ''
    },
    activeMembers() {
      return this.members.filter(countsForDues).sort((a, b) => a.name.localeCompare(b.name))
    },
    sortedPayments() {
      return sortPayments(this.payments)
    },
    filteredPayments() {
      return filterPayments(this.sortedPayments, this.filters)
    },
    hasActiveFilters() {
      return !!this.filters.search.trim() || this.filters.method !== 'ALL'
    },
    figures() {
      const summary = paymentsSummary(this.payments, this.currentPeriod)
      return [
        { label: 'This month', value: formatMoney(summary.thisMonth) },
        { label: 'All time', value: formatMoney(summary.allTime) },
        { label: 'Average payment', value: formatMoney(summary.average) }
      ]
    }
  },
  watch: {
    // On the phone sheet the month covered starts at the oldest month the member has not paid
    'form.memberId'() {
      if (!this.wide && this.owed.oldest) this.form.period = this.owed.oldest
    }
  },
  async created() {
    this.wideQuery = window.matchMedia?.(WIDE)
    this.wide = this.wideQuery ? this.wideQuery.matches : true
    this.wideQuery?.addEventListener('change', this.onWide)
    // a member's page links here with their name to show only their payments
    const search = this.$route?.query?.search
    if (typeof search === 'string') this.filters.search = search
    await this.loadData()
    this.openForQueryMember()
  },
  beforeUnmount() {
    this.wideQuery?.removeEventListener('change', this.onWide)
  },
  methods: {
    onWide(event) {
      this.wide = event.matches
    },
    stripArgs(member) {
      return {
        currentMonth: this.currentPeriod,
        joinDate: member.joinDate || '',
        paidMonths: this.paidByMember.get(member.id) || new Set(),
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member)
      }
    },
    stripProps(member) {
      return { ...this.stripArgs(member), label: `Dues for ${member.name}, last 12 months` }
    },
    // Members are only needed to record a payment (STAFF and above); the history carries each member's name
    async loadData() {
      try {
        const [members, payments] = await Promise.all([
          this.authStore.isStaff ? api.getMembers() : [],
          api.getPayments()
        ])
        this.members = Array.isArray(members) ? members : []
        this.payments = Array.isArray(payments) ? payments : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading payments:', error)
        this.members = []
        this.payments = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    clearFilters() {
      this.filters = { ...EMPTY_FILTERS }
    },
    // /payments?memberId=<id> (the Members phone card) opens the dialog with that member chosen (STAFF and above);
    // the parameter is dropped so a reload or a close does not bring the dialog back
    openForQueryMember() {
      const memberId = this.$route?.query?.memberId
      if (memberId === undefined) return
      this.$router.replace({ query: { ...this.$route.query, memberId: undefined } })
      if (!this.authStore.isStaff || !this.loaded || this.loadError) return
      this.openRecord()
      if (this.activeMembers.some(member => String(member.id) === String(memberId))) this.form.memberId = String(memberId)
    },
    openRecord() {
      this.form = emptyForm()
      this.today = localISODate()
      this.formError = ''
      this.formErrors = { ...EMPTY_ERRORS }
      this.recordOpen = true
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
        await this.loadData()
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

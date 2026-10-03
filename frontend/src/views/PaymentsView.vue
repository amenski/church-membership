<template>
  <div>
    <PageHead title="Payments" lead="Record what members paid and see the history.">
      <template v-if="authStore.isStaff" #actions>
        <BaseButton variant="secondary" @click="exportPayments">
          <i class="bi bi-download tw:mr-2" aria-hidden="true"></i>Export CSV
        </BaseButton>
        <BaseButton @click="openRecord">
          <i class="bi bi-plus-lg tw:mr-2" aria-hidden="true"></i>Record payment
        </BaseButton>
      </template>
    </PageHead>

    <AlertBanner v-if="loadError">
      <div class="tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-3">
        <span>The payments did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadData">Try again</BaseButton>
      </div>
    </AlertBanner>

    <p v-if="!loaded" class="tw:m-0 tw:py-4 tw:text-(length:--text-body) tw:text-muted" role="status">Loading payments...</p>

    <!-- Quiet figures: from the loaded payments, no coloured tiles -->
    <dl v-if="payments.length" class="tw:mt-0 tw:mb-8 tw:flex tw:border-y tw:border-rule tw:py-4 tw:max-sm:flex-col tw:max-sm:gap-2">
      <div
        v-for="figure in figures"
        :key="figure.label"
        class="tw:flex-1 tw:px-6 tw:first:pl-0 tw:not-first:border-l tw:not-first:border-rule tw:max-sm:flex tw:max-sm:items-baseline tw:max-sm:justify-between tw:max-sm:px-0 tw:max-sm:not-first:border-l-0"
      >
        <dt class="tw:text-base tw:font-medium tw:text-muted">{{ figure.label }}</dt>
        <dd :class="[FIGURE, 'tw:m-0 tw:text-2xl tw:leading-[1.3] tw:max-sm:text-xl']">{{ figure.value }}</dd>
      </div>
    </dl>

    <!-- Filters -->
    <form v-if="payments.length" class="tw:mb-6 tw:grid tw:grid-cols-2 tw:gap-3 tw:md:flex tw:md:flex-wrap tw:md:items-end" role="search" aria-label="Filter payments" @submit.prevent>
      <div class="tw:col-span-2 tw:md:col-span-1 tw:md:min-w-60 tw:md:flex-1">
        <label for="filter-search" :class="LABEL">Search</label>
        <input id="filter-search" v-model="filters.search" type="search" placeholder="Search member name" autocomplete="off" :class="CONTROL">
      </div>
      <div class="tw:col-span-2 tw:md:col-span-1 tw:md:w-48">
        <label for="filter-method" :class="LABEL">Method</label>
        <select id="filter-method" v-model="filters.method" :class="CONTROL">
          <option value="ALL">All methods</option>
          <option v-for="method in PAYMENT_METHODS" :key="method.value" :value="method.value">{{ method.label }}</option>
        </select>
      </div>
      <TextButton v-if="hasActiveFilters && filteredPayments.length" class="tw:col-span-2 tw:text-left tw:md:col-span-1 tw:md:py-1.5" @click="clearFilters">Clear filters</TextButton>
    </form>

    <!-- Empty states -->
    <div v-if="loaded && !loadError && !payments.length">
      <EmptyNote>No payments yet. Record the first one.</EmptyNote>
      <BaseButton v-if="authStore.isStaff" class="tw:mt-2" @click="openRecord">Record payment</BaseButton>
    </div>
    <div v-else-if="payments.length && !filteredPayments.length">
      <EmptyNote>No payments match these filters.</EmptyNote>
      <BaseButton variant="secondary" class="tw:mt-2" @click="clearFilters">Clear filters</BaseButton>
    </div>

    <template v-if="filteredPayments.length">
      <p class="tw:mt-0 tw:mb-2 tw:text-sm tw:text-muted" aria-live="polite">
        {{ hasActiveFilters ? `${filteredPayments.length} of ${payments.length} payments` : `${payments.length} ${payments.length === 1 ? 'payment' : 'payments'}` }}
      </p>

      <!-- md and up: ruled table -->
      <table class="tw:hidden tw:w-full tw:border-collapse tw:text-left tw:text-(length:--text-body) tw:tabular-nums tw:md:table">
        <caption class="tw:sr-only">Payment history, newest first</caption>
        <thead>
          <tr class="tw:border-b tw:border-rule">
            <th scope="col" :class="TH">Paid on</th>
            <th scope="col" :class="TH">Member</th>
            <th scope="col" :class="TH">Month covered</th>
            <th scope="col" :class="TH">Method</th>
            <th scope="col" :class="[TH, 'tw:text-right']">Amount</th>
            <th scope="col" :class="TH">Receipt</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="payment in filteredPayments" :key="payment.id" class="tw:h-(--row-h) tw:border-b tw:border-rule">
            <td :class="[TD, 'tw:whitespace-nowrap']">{{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</td>
            <td :class="[TD, 'tw:max-w-0 tw:w-[30%] tw:font-medium tw:[overflow-wrap:anywhere]']">{{ payment.member?.name || 'Unknown' }}</td>
            <td :class="[TD, 'tw:whitespace-nowrap']">{{ periodLabel(payment.period) }}</td>
            <td :class="[TD, 'tw:whitespace-nowrap']">{{ methodLabel(payment.paymentMethod) }}</td>
            <td :class="[TD, 'tw:text-right tw:whitespace-nowrap']">{{ formatMoney(payment.amount) }}</td>
            <td :class="TD">
              <TextButton class="tw:py-1" @click="openReceipt(payment)">
                {{ receiptNumber(payment) }}<span class="tw:sr-only">, receipt for {{ payment.member?.name || 'Unknown' }}</span>
              </TextButton>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Below md: the same rows, stacked -->
      <ul class="tw:m-0 tw:list-none tw:border-t tw:border-rule tw:p-0 tw:md:hidden">
        <li v-for="payment in filteredPayments" :key="payment.id" class="tw:border-b tw:border-rule tw:py-3">
          <div class="tw:flex tw:items-baseline tw:justify-between tw:gap-3">
            <span class="tw:min-w-0 tw:font-medium tw:[overflow-wrap:anywhere]">{{ payment.member?.name || 'Unknown' }}</span>
            <span class="tw:shrink-0 tw:font-medium tw:tabular-nums">{{ formatMoney(payment.amount) }}</span>
          </div>
          <div class="tw:text-sm tw:text-muted">{{ periodLabel(payment.period) }} &middot; {{ methodLabel(payment.paymentMethod) }}</div>
          <div class="tw:flex tw:items-center tw:justify-between tw:gap-3 tw:text-sm tw:text-muted tw:tabular-nums">
            <span>Paid on {{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</span>
            <TextButton class="tw:text-base" @click="openReceipt(payment)">
              {{ receiptNumber(payment) }}<span class="tw:sr-only">, receipt for {{ payment.member?.name || 'Unknown' }}</span>
            </TextButton>
          </div>
        </li>
      </ul>
    </template>

    <!-- Record payment (STAFF and above) -->
    <BaseModal v-if="authStore.isStaff" v-model="recordOpen" title="Record payment" size="md">
      <AlertBanner v-if="formError">{{ formError }}</AlertBanner>
      <EmptyNote v-if="!activeMembers.length">There are no active members to record a payment for. Add or reactivate a member first.</EmptyNote>
      <form v-else id="payment-form" class="tw:flex tw:flex-col tw:gap-4" novalidate @submit.prevent="recordPayment">
        <BaseSelect id="payment-member" v-model="form.memberId" label="Member" :error="formErrors.memberId">
          <option value="" disabled>Choose a member</option>
          <option v-for="member in activeMembers" :key="member.id" :value="String(member.id)">{{ member.name }}</option>
        </BaseSelect>
        <div class="tw:grid tw:grid-cols-1 tw:gap-4 tw:sm:grid-cols-2">
          <BaseInput id="payment-period" v-model="form.period" label="Month covered" type="month" :max="currentPeriod" :error="formErrors.period" />
          <BaseInput id="payment-date" v-model="form.paymentDate" label="Paid on" type="date" :max="today" hint="Change this when you enter an older payment." :error="formErrors.paymentDate" />
        </div>
        <div class="tw:grid tw:grid-cols-1 tw:gap-4 tw:sm:grid-cols-2">
          <BaseInput id="payment-amount" v-model="form.amount" label="Amount" type="number" min="0.01" step="0.01" inputmode="decimal" :error="formErrors.amount" />
          <BaseSelect id="payment-method" v-model="form.paymentMethod" label="Payment method" :error="formErrors.paymentMethod">
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
    <BaseModal v-model="receiptOpen" :title="selectedPayment ? `Receipt ${receiptNumber(selectedPayment)}` : 'Receipt'" size="sm">
      <!-- A plain element for html2pdf to capture: token hex colours only, no tinted or blended colours -->
      <div v-if="selectedPayment" ref="receiptContent" class="tw:bg-paper tw:p-2 tw:text-ink">
        <p class="tw:m-0 tw:mb-3 tw:font-display tw:text-lg tw:font-bold">Felege Selam</p>
        <dl class="tw:m-0 tw:grid tw:grid-cols-[auto_1fr] tw:gap-x-6 tw:gap-y-2 tw:text-base">
          <dt class="tw:font-normal tw:text-muted">Receipt</dt>
          <dd class="tw:m-0 tw:font-medium">{{ receiptNumber(selectedPayment) }}</dd>
          <dt class="tw:font-normal tw:text-muted">Member</dt>
          <dd class="tw:m-0 tw:font-medium tw:[overflow-wrap:anywhere]">{{ selectedPayment.member?.name || 'Unknown' }}</dd>
          <dt class="tw:font-normal tw:text-muted">Month covered</dt>
          <dd class="tw:m-0">{{ periodLabel(selectedPayment.period) }}</dd>
          <dt class="tw:font-normal tw:text-muted">Paid on</dt>
          <dd class="tw:m-0">{{ formatDate(selectedPayment.paymentDate, 'MMM d, yyyy') }}</dd>
          <dt class="tw:font-normal tw:text-muted">Method</dt>
          <dd class="tw:m-0">{{ methodLabel(selectedPayment.paymentMethod) }}</dd>
          <template v-if="selectedPayment.notes">
            <dt class="tw:font-normal tw:text-muted">Notes</dt>
            <dd class="tw:m-0 tw:[overflow-wrap:anywhere]">{{ selectedPayment.notes }}</dd>
          </template>
        </dl>
        <p class="tw:mt-4 tw:mb-0 tw:border-t tw:border-rule tw:pt-3 tw:text-muted">Amount</p>
        <p :class="[FIGURE, 'tw:m-0 tw:text-2xl']">{{ formatMoney(selectedPayment.amount) }}</p>
      </div>
      <template #footer>
        <BaseButton variant="secondary" @click="receiptOpen = false">Close</BaseButton>
        <BaseButton :disabled="downloading" :aria-busy="downloading ? 'true' : undefined" @click="downloadReceipt">
          <i class="bi bi-download tw:mr-2" aria-hidden="true"></i>{{ downloading ? 'Preparing...' : 'Download PDF' }}
        </BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAppStore } from '../stores/appStore'
import { useAuthStore } from '../stores/authStore'
import { downloadBlob, formatDate, formatMoney, localISODate } from '@/utils'
import { buildPaymentRequest, PAYMENT_METHODS } from '@/utils/paymentPayload'
import { filterPayments, methodLabel, paymentsSummary, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import TextButton from '@/components/TextButton.vue'

const LABEL = 'tw:mb-1 tw:block tw:text-(length:--text-label) tw:leading-(--lh-label) tw:font-medium tw:text-muted'
const CONTROL = 'tw:block tw:h-(--control-h) tw:w-full tw:rounded-md tw:border tw:border-field tw:bg-paper tw:px-3 tw:text-(length:--text-body) tw:text-ink tw:placeholder:text-muted tw:placeholder:opacity-80 tw:focus:border-teal tw:focus:outline-2 tw:focus:outline-offset-1 tw:focus:outline-teal'
const TH = 'tw:px-3 tw:py-2 tw:text-[0.9375rem] tw:font-medium tw:text-muted tw:first:pl-0 tw:last:pr-0'
const TD = 'tw:px-3 tw:py-2 tw:align-middle tw:first:pl-0 tw:last:pr-0'
// Big figures: Alegreya, tabular and lining so numbers line up
const FIGURE = 'tw:font-display tw:font-bold tw:tabular-nums tw:lining-nums'

const NOTES_MAX = 500
const EMPTY_ERRORS = { memberId: '', period: '', paymentDate: '', amount: '', paymentMethod: '', notes: '' }
const EMPTY_FILTERS = { search: '', method: 'ALL' }

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
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, EmptyNote, PageHead, TextButton },
  setup() {
    return {
      appStore: useAppStore(),
      authStore: useAuthStore(),
      formatDate,
      formatMoney,
      methodLabel,
      periodLabel,
      receiptNumber,
      LABEL,
      CONTROL,
      TH,
      TD,
      FIGURE,
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
      downloading: false
    }
  },
  computed: {
    currentPeriod() {
      return this.today.slice(0, 7)
    },
    activeMembers() {
      return this.members.filter(member => member.active).sort((a, b) => a.name.localeCompare(b.name))
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
  async created() {
    await this.loadData()
  },
  methods: {
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
    async downloadReceipt() {
      this.downloading = true
      try {
        const options = {
          margin: 1,
          filename: `receipt-${receiptNumber(this.selectedPayment)}.pdf`,
          image: { type: 'jpeg', quality: 0.98 },
          html2canvas: { scale: 2 },
          jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' }
        }
        const { default: html2pdf } = await import('html2pdf.js')
        await html2pdf().set(options).from(this.$refs.receiptContent).save()
      } catch (error) {
        console.error('Error creating the receipt PDF:', error)
        this.notify('error', 'Could not create the PDF', 'Try again, or close this window and open the receipt again.')
      } finally {
        this.downloading = false
      }
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

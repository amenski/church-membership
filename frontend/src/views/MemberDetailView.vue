<template>
  <div>
    <router-link to="/members" class="mb-2 inline-flex min-h-11 items-center gap-1 text-base font-medium">
      <Icon name="chevron-left" :size="16" />Members
    </router-link>

    <p v-if="state === 'loading'" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading member...</p>

    <!-- A member that does not exist, or is hidden (archived, for anyone but an administrator) -->
    <div v-else-if="state === 'missing'">
      <PageHead title="Member not found" />
      <p class="m-0 mb-4 text-base">This member does not exist, or is not on the list any more.</p>
      <BaseButton to="/members">Back to Members</BaseButton>
    </div>

    <AlertBanner v-else-if="state === 'error'">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The member did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="load">Try again</BaseButton>
      </div>
    </AlertBanner>

    <template v-else-if="member">
      <PageHead :title="member.name" :lead="member.joinDate ? `Member since ${formatDate(member.joinDate, 'MMM yyyy')}` : ''">
        <template #meta>
          <StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel>
        </template>
        <template v-if="!archived" #actions>
          <BaseButton variant="secondary" to="/communications" class="max-lg:hidden">Send message</BaseButton>
          <BaseButton v-if="canRecordPayment" :to="payLink" class="max-lg:hidden">Record payment</BaseButton>
          <BaseButton v-if="authStore.isAdmin" variant="secondary" class="max-lg:hidden border-clay! text-clay!" @click="archiveOpen = true">Archive</BaseButton>
        </template>
      </PageHead>

      <div v-if="archived" role="note" class="mb-4 rounded-md border border-rule bg-paper px-4 py-2.5 text-sm text-ink">
        Archived members are hidden from the lists, dues, reminders and messages. Their payments and messages are kept. Restore this member from Members, Archived.
      </div>

      <div class="flex flex-wrap items-start gap-5">
        <div class="flex min-w-0 flex-[1_1_640px] flex-col gap-5">
          <!-- Dues by month: 24 months in two rows from lg, 12 on a phone with the two actions under it -->
          <section :class="CARD" aria-labelledby="dues-title">
            <div class="mb-3 flex items-baseline justify-between gap-2">
              <h2 id="dues-title" class="m-0 text-base font-semibold text-ink">Dues by month</h2>
              <StatusLabel v-if="countsForDues && cells" :tone="member.consecutiveMonthsMissed > 0 ? 'behind' : 'paid'">
                {{ member.consecutiveMonthsMissed > 0 ? monthsBehind(member.consecutiveMonthsMissed) : 'Paid up' }}
              </StatusLabel>
            </div>
            <p v-if="paymentsFailed" class="m-0 text-base text-muted">The payments did not load, so the months cannot be drawn. Try again.</p>
            <template v-else-if="cells">
              <p class="mt-0 mb-4 text-lg leading-snug">{{ owedSentence }}</p>
              <YearStrip class="max-lg:hidden" size="detail" :months="24" v-bind="stripProps" label="Dues, last 24 months" />
              <YearStrip class="lg:hidden" size="large" v-bind="stripProps" :label="`Dues for ${member.name}, last 12 months`" />
              <ul class="m-0 mt-4 flex list-none flex-wrap gap-x-4 gap-y-2 p-0 text-xs text-muted max-lg:hidden" aria-hidden="true">
                <li v-for="item in LEGEND" :key="item.label" class="flex items-center gap-1.5">
                  <span :class="['box-border block size-3.5 rounded-sm', SQUARES[item.state]]"></span>{{ item.label }}
                </li>
              </ul>
            </template>
            <div v-if="member.phone || canRecordPayment" class="mt-4 flex gap-2 lg:hidden">
              <a v-if="member.phone" :href="telHref(member.phone)" :class="[ACTION, OUTLINE, 'flex-1']">
                <Icon name="phone" :size="18" />Call<span class="sr-only"> {{ member.name }}</span>
              </a>
              <router-link v-if="canRecordPayment" :to="payLink" :class="[ACTION, PRIMARY, 'flex-[1.4]']">
                Record payment<span class="sr-only"> for {{ member.name }}</span>
              </router-link>
            </div>
          </section>

          <section :class="CARD" aria-labelledby="payments-title">
            <SectionTitle id="payments-title">Payments</SectionTitle>
            <p v-if="paymentsFailed" class="m-0 text-base text-muted">The payments did not load. Check your connection and reload the page.</p>
            <EmptyNote v-else-if="!payments.length">No payments yet.<template v-if="canRecordPayment"> Record the first one.</template></EmptyNote>
            <template v-else>
              <table :class="TABLE">
                <caption class="sr-only">Latest payments, newest first</caption>
                <thead>
                  <tr class="border-b border-rule">
                    <th scope="col" :class="TH">Receipt</th>
                    <th scope="col" :class="TH">Month</th>
                    <th scope="col" :class="TH">Paid on</th>
                    <th scope="col" :class="TH">Method</th>
                    <th scope="col" :class="[TH, 'text-right']">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="payment in latestPayments" :key="payment.id" class="h-(--row-h) border-b border-rule">
                    <td :class="TD">
                      <TextButton class="py-1" @click="openReceipt(payment)">
                        {{ receiptNumber(payment) }}<span class="sr-only">, receipt for {{ periodLabel(payment.period) }}</span>
                      </TextButton>
                    </td>
                    <td :class="[TD, 'whitespace-nowrap']">{{ periodLabel(payment.period) }}</td>
                    <td :class="[TD, 'whitespace-nowrap']">{{ formatDate(payment.paymentDate, 'MMM d, yyyy') }}</td>
                    <td :class="[TD, 'whitespace-nowrap']">{{ methodLabel(payment.paymentMethod) }}</td>
                    <td :class="[TD, 'text-right font-medium whitespace-nowrap']">{{ formatMoney(payment.amount) }}</td>
                  </tr>
                </tbody>
              </table>
              <ul class="m-0 list-none p-0 md:hidden">
                <li v-for="payment in latestPayments" :key="payment.id" class="flex items-center justify-between gap-3 border-b border-rule py-2">
                  <div class="min-w-0">
                    <div class="text-lg font-medium">{{ periodLabel(payment.period) }}</div>
                    <div class="text-sm text-muted tabular-nums">
                      <TextButton class="inline-flex min-h-11 items-center" @click="openReceipt(payment)">
                        {{ receiptNumber(payment) }}<span class="sr-only">, receipt for {{ periodLabel(payment.period) }}</span>
                      </TextButton>, {{ methodLabel(payment.paymentMethod) }}, {{ formatDate(payment.paymentDate, 'MMM d') }}
                    </div>
                  </div>
                  <div class="shrink-0 text-lg font-medium tabular-nums">{{ formatMoney(payment.amount) }}</div>
                </li>
              </ul>
              <p class="mt-3 mb-0 text-sm text-muted">
                {{ payments.length > latestPayments.length ? `Showing the latest ${latestPayments.length} of ${payments.length} payments.` : `${payments.length} ${payments.length === 1 ? 'payment' : 'payments'}.` }}
                <router-link :to="{ path: '/payments', query: { search: member.name } }" class="inline-flex min-h-11 items-center lg:min-h-0">All payments</router-link>
              </p>
            </template>
          </section>
        </div>

        <div class="flex min-w-0 flex-[0_1_340px] flex-col gap-5 max-lg:basis-full lg:min-w-[300px]">
          <section :class="CARD" aria-labelledby="contact-title">
            <SectionTitle id="contact-title">Contact</SectionTitle>
            <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-base">
              <dt class="text-muted">Phone</dt>
              <dd class="m-0 [overflow-wrap:anywhere]">
                <a v-if="member.phone" :href="telHref(member.phone)">{{ member.phone }}</a>
                <template v-else><span aria-hidden="true">&ndash;</span><span class="sr-only">No phone</span></template>
              </dd>
              <dt class="text-muted">Email</dt>
              <dd class="m-0 [overflow-wrap:anywhere]">
                <template v-if="member.email">{{ member.email }}</template>
                <template v-else><span aria-hidden="true">&ndash;</span><span class="sr-only">No email</span></template>
              </dd>
              <dt class="text-muted">Joined</dt>
              <dd class="m-0">{{ member.joinDate ? formatDate(member.joinDate, 'MMM d, yyyy') : '' }}</dd>
              <dt class="text-muted">Status</dt>
              <dd class="m-0"><StatusLabel :tone="statusTone(member.status)">{{ statusLabel(member.status) }}</StatusLabel></dd>
              <dt class="text-muted">Last paid</dt>
              <dd class="m-0">{{ member.lastPaymentDate ? formatDate(member.lastPaymentDate, 'MMM d, yyyy') : 'Never' }}</dd>
            </dl>
            <div v-if="member.phone || canEdit" class="mt-4 flex flex-wrap gap-2">
              <a v-if="member.phone" :href="telHref(member.phone)" :class="[ACTION, OUTLINE, 'max-lg:hidden']">
                <Icon name="phone" :size="16" />Call<span class="sr-only"> {{ member.name }}</span>
              </a>
              <button v-if="canEdit" type="button" :class="[ACTION, OUTLINE, 'max-lg:flex-1']" @click="formOpen = true">Edit details</button>
            </div>
          </section>

          <section v-if="household" :class="CARD" aria-labelledby="household-title">
            <SectionTitle id="household-title">Household</SectionTitle>
            <p class="m-0 mb-1 text-base font-medium [overflow-wrap:anywhere]">{{ household.name }}</p>
            <ul class="m-0 list-none border-t border-rule p-0">
              <li v-for="other in householdMembers" :key="other.id" class="flex min-h-11 flex-wrap items-center justify-between gap-x-3 border-b border-rule py-2">
                <span class="min-w-0 [overflow-wrap:anywhere]">
                  <span v-if="other.id === member.id">{{ other.name }} (this member)</span>
                  <router-link v-else :to="`/members/${other.id}`">{{ other.name }}</router-link>
                </span>
                <StatusLabel :tone="other.tone">{{ other.text }}</StatusLabel>
              </li>
              <li v-for="person in dependents" :key="`person-${person.id}`" class="flex min-h-11 flex-wrap items-center justify-between gap-x-3 border-b border-rule py-2">
                <span class="min-w-0 [overflow-wrap:anywhere]">{{ person.name }}</span>
                <span class="text-sm text-muted">No membership</span>
              </li>
            </ul>
            <router-link to="/households" class="mt-3 inline-flex min-h-11 items-center text-base lg:min-h-0">Open household</router-link>
          </section>
        </div>
      </div>

      <!-- Phone only: the two actions the header holds from lg up -->
      <div v-if="!archived" class="mt-5 flex flex-col gap-2 lg:hidden">
        <router-link to="/communications" :class="[ACTION, OUTLINE]">Send message</router-link>
        <button v-if="authStore.isAdmin" type="button" :class="[ACTION, DANGER]" @click="archiveOpen = true">Archive</button>
      </div>

      <MemberFormDialog v-if="canEdit" v-model="formOpen" :member="member" @saved="load" />
      <MemberArchiveDialog v-if="authStore.isAdmin" v-model="archiveOpen" :member="member" @archived="onArchived" />
      <ReceiptDialog v-model="receiptOpen" :payment="selectedPayment" />
    </template>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAuthStore } from '../stores/authStore'
import { formatDate, formatMoney, localISODate } from '@/utils'
import { monthsBehind, owedSummary } from '@/utils/dues'
import { countsForDues, isArchived, statusLabel, statusTone } from '@/utils/memberStatus'
import { methodLabel, periodLabel, receiptNumber, sortPayments } from '@/utils/paymentHistory'
import { SQUARES, stripCells } from '@/utils/yearStrip'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import Icon from '@/components/Icon.vue'
import MemberArchiveDialog from '@/components/MemberArchiveDialog.vue'
import MemberFormDialog from '@/components/MemberFormDialog.vue'
import PageHead from '@/components/PageHead.vue'
import ReceiptDialog from '@/components/ReceiptDialog.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'
import { CARD, TABLE, TABLE_TH as TH, TABLE_TD as TD } from '@/ui/classes'

const LATEST = 6
const MONTHS = 24
// A 44px tap target on a phone, the control height from lg up
const ACTION = 'flex items-center justify-center gap-2 rounded-sm border px-3 text-base font-medium no-underline min-h-(--control-h) max-lg:min-h-11 max-lg:text-lg lg:inline-flex'
const OUTLINE = 'border-field bg-paper text-ink hover:border-teal hover:bg-teal-tint'
const PRIMARY = 'border-teal bg-teal text-paper hover:bg-teal-hover'
const DANGER = 'border-clay bg-paper text-clay hover:bg-clay-tint'
// Legend swatches: Paid, Missed, Due now, Not a member (the strip's own squares)
const LEGEND = [
  { state: 'paid', label: 'Paid' },
  { state: 'missed', label: 'Missed' },
  { state: 'due', label: 'Due now' },
  { state: 'none', label: 'Not a member' }
]

export default {
  name: 'MemberDetailView',
  components: { AlertBanner, BaseButton, EmptyNote, Icon, MemberArchiveDialog, MemberFormDialog, PageHead, ReceiptDialog, SectionTitle, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      authStore: useAuthStore(),
      formatDate,
      formatMoney,
      methodLabel,
      monthsBehind,
      periodLabel,
      receiptNumber,
      statusLabel,
      statusTone,
      ACTION,
      CARD,
      DANGER,
      LEGEND,
      OUTLINE,
      PRIMARY,
      SQUARES,
      TABLE,
      TH,
      TD
    }
  },
  data() {
    return {
      // loading | ready | missing | error
      state: 'loading',
      member: null,
      payments: [],
      paymentsFailed: false,
      household: null,
      // id -> member from the Members list, only to say "3 months behind" for the household's other members
      membersById: new Map(),
      today: localISODate(),
      formOpen: false,
      archiveOpen: false,
      receiptOpen: false,
      selectedPayment: null,
      loadToken: 0
    }
  },
  computed: {
    archived() {
      return isArchived(this.member)
    },
    countsForDues() {
      return countsForDues(this.member)
    },
    // an archived member is read-only here: the Status select cannot hold Archived
    canEdit() {
      return this.authStore.isStaff && !this.archived
    },
    canRecordPayment() {
      return this.authStore.isStaff && this.countsForDues
    },
    payLink() {
      return { path: '/payments', query: { memberId: this.member.id } }
    },
    latestPayments() {
      return sortPayments(this.payments).slice(0, LATEST)
    },
    // the arguments the strip and the owed sentence share (the same rule as every other strip: utils/yearStrip.js)
    stripArgs() {
      return {
        currentMonth: this.today.slice(0, 7),
        joinDate: this.member.joinDate || '',
        paidMonths: new Set(this.payments.map(payment => String(payment.period))),
        monthsMissed: this.member.consecutiveMonthsMissed || 0,
        countsForDues: this.countsForDues
      }
    },
    stripProps() {
      return { ...this.stripArgs, muted: this.archived }
    },
    cells() {
      return this.paymentsFailed ? null : stripCells({ ...this.stripArgs, count: MONTHS })
    },
    owedSentence() {
      if (this.countsForDues) return owedSummary(this.cells).sentence
      return `Dues are not tracked while this member is ${statusLabel(this.member.status).toLowerCase()}.`
    },
    // this member first, then the others, each with the words that say how they stand
    householdMembers() {
      return (this.household?.members || []).map(other => {
        const known = this.membersById.get(other.id)
        if (other.status === 'MEMBER' && known) {
          const behind = known.consecutiveMonthsMissed > 0
          return { ...other, tone: behind ? 'behind' : 'paid', text: behind ? monthsBehind(known.consecutiveMonthsMissed) : 'Paid up' }
        }
        return { ...other, tone: statusTone(other.status), text: statusLabel(other.status) }
      })
    },
    // people on the household with no membership (children, a spouse who pays no dues)
    dependents() {
      return (this.household?.people || []).filter(person => !person.memberStatus)
    }
  },
  watch: {
    // from a household member's link to another member: the same component, a new id
    '$route.params.id'() {
      if (this.$route.name === 'member') this.load()
    }
  },
  created() {
    return this.load()
  },
  methods: {
    telHref(phone) {
      return `tel:${phone.replace(/[^+\d]/g, '')}`
    },
    openReceipt(payment) {
      this.selectedPayment = payment
      this.receiptOpen = true
    },
    // The member, then (together) their payments and household. A later load (another member, or a reload after an
    // edit) makes an earlier one that is still running stop.
    async load() {
      const token = ++this.loadToken
      const id = this.$route.params.id
      this.today = localISODate()
      if (!this.member || String(this.member.id) !== String(id)) this.state = 'loading'
      let member
      try {
        member = await api.getMember(id)
      } catch (error) {
        if (token !== this.loadToken) return
        console.error('Error loading member:', error)
        const status = error.response?.status
        this.member = null
        this.state = status === 404 || status === 400 ? 'missing' : 'error'
        return
      }
      const [payments, household] = await Promise.all([this.loadPayments(member.id), this.loadHousehold(member)])
      if (token !== this.loadToken) return
      this.member = member
      this.paymentsFailed = payments === null
      this.payments = payments || []
      this.household = household
      this.state = 'ready'
    },
    async loadPayments(id) {
      try {
        const data = await api.getPaymentsByMember(id)
        return Array.isArray(data) ? data : []
      } catch (error) {
        console.error('Error loading the payments of a member:', error)
        return null
      }
    },
    // a failure only hides the card: the rest of the page does not need it
    async loadHousehold(member) {
      if (!member.householdId) return null
      try {
        const household = await api.getHousehold(member.householdId)
        if ((household.members || []).some(other => other.id !== member.id)) await this.loadMembersById()
        return household
      } catch (error) {
        console.error('Error loading the household of a member:', error)
        return null
      }
    },
    async loadMembersById() {
      try {
        const members = await api.getMembers()
        this.membersById = new Map((Array.isArray(members) ? members : []).map(member => [member.id, member]))
      } catch (error) {
        console.error('Error loading members for the household card:', error)
        this.membersById = new Map()
      }
    },
    onArchived() {
      this.$router.push('/members')
    }
  }
}
</script>

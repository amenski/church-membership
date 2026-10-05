<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead title="Messages" lead="Email members and see what was delivered." band />

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>The messages did not load. Check your connection and try again.</span>
        <BaseButton variant="secondary" size="sm" @click="loadData">Try again</BaseButton>
      </div>
    </AlertBanner>

    <div>
      <!-- Compose and who gets it (STAFF and above): side by side from lg, stacked below -->
      <div v-if="authStore.isStaff" class="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,28rem)] lg:items-start">
        <section :class="CARD" aria-labelledby="compose-title">
          <SectionTitle id="compose-title">New message</SectionTitle>
          <AlertBanner v-if="sendError">{{ sendError }}</AlertBanner>
          <form class="flex flex-col gap-4" novalidate @submit.prevent="askToSend">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <BaseSelect id="message-audience" v-model="form.recipientType" label="Send to">
                <option value="ALL">Everyone</option>
                <option value="OVERDUE">Behind on dues</option>
                <option value="SPECIFIC">One member</option>
              </BaseSelect>
              <BaseInput
                v-if="form.recipientType === 'OVERDUE'"
                id="message-months"
                v-model="form.monthsOverdue"
                label="At least this many months behind"
                type="number"
                min="1"
                step="1"
                inputmode="numeric"
                :error="formErrors.monthsOverdue"
              />
              <BaseSelect v-else-if="form.recipientType === 'SPECIFIC'" id="message-member" v-model="form.memberId" label="Member" :error="formErrors.memberId">
                <option value="" disabled>Choose a member</option>
                <option v-for="member in activeMembers" :key="member.id" :value="String(member.id)">{{ member.name }}{{ member.email ? '' : ' (no email)' }}</option>
              </BaseSelect>
            </div>
            <BaseInput id="message-subject" v-model="form.subject" label="Subject" maxlength="200" autocomplete="off" :error="formErrors.subject" />
            <BaseTextarea
              id="message-body"
              v-model="form.message"
              label="Message"
              :rows="6"
              :max="MESSAGE_MAX"
              hint="Write {{member_name}} to insert each member's name."
              :error="formErrors.message"
            />
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
              <BaseButton type="submit" :disabled="sending" :aria-busy="sending ? 'true' : undefined" class="max-sm:w-full">
                {{ sending ? 'Sending...' : `Send to ${personLabel(recipientTotal)}` }}
              </BaseButton>
              <span v-if="skippedNote" class="text-sm text-ochre-text">{{ skippedNote }}</span>
            </div>
          </form>
        </section>

        <!-- Who gets this: computed from the members and payments already loaded -->
        <section :class="CARD" aria-labelledby="who-title">
          <SectionTitle id="who-title">Who gets this</SectionTitle>
          <p class="mt-0 mb-3 text-sm text-muted" role="status">{{ summaryText }}</p>
          <ul v-if="recipients.length" class="m-0 max-h-[32rem] list-none overflow-y-auto border-t border-rule p-0">
            <li v-for="row in recipients" :key="row.member.id" class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 border-b border-rule py-2.5">
              <div class="min-w-0">
                <div class="font-medium text-ink [overflow-wrap:anywhere]">{{ row.member.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ row.member.consecutiveMonthsMissed > 0 ? monthsBehind(row.member.consecutiveMonthsMissed) : 'Paid up' }}</div>
              </div>
              <YearStrip v-if="paidByMember" v-bind="stripProps(row.member)" />
              <div class="flex basis-full flex-wrap items-center gap-x-2 gap-y-1">
                <StatusBadge v-if="row.state === 'send'" tone="paid">Will get the email</StatusBadge>
                <StatusBadge v-else-if="row.state === 'noEmail'" tone="behind">Skipped, no email</StatusBadge>
                <StatusBadge v-else tone="muted">Skipped, shares an address</StatusBadge>
                <span v-if="row.state === 'send'" class="text-xs text-muted [overflow-wrap:anywhere]">{{ row.member.email }}</span>
                <span v-else-if="row.state === 'shared'" class="text-xs text-muted">{{ row.sharesWith }} gets the one copy</span>
                <span v-else-if="row.member.phone" class="text-xs text-muted">
                  <a :href="`tel:${row.member.phone.replace(/[^+\d]/g, '')}`" class="text-teal underline underline-offset-[3px] hover:text-teal-hover">Call {{ row.member.phone }}<span class="sr-only"> for {{ row.member.name }}</span></a> instead
                </span>
                <span v-else class="text-xs text-muted">No phone on file</span>
              </div>
            </li>
          </ul>
          <p class="mt-3 mb-0 text-sm text-muted">Members without an email are never sent a message.</p>
        </section>
      </div>

      <!-- History -->
      <section aria-labelledby="history-title">
        <SectionTitle id="history-title">Sent messages</SectionTitle>

        <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">Loading messages...</p>

        <template v-else-if="!loadError">
          <div v-if="!messages.length">
            <EmptyNote>No messages yet.<template v-if="authStore.isStaff"> Use the form above to send the first one.</template></EmptyNote>
          </div>

          <!-- below lg: one row a message -->
          <ul v-if="messages.length" class="m-0 list-none border-t border-rule p-0 lg:hidden">
            <li v-for="message in messages" :key="message.id" class="flex items-start justify-between gap-4 border-b border-rule py-3 text-(length:--text-body) max-sm:flex-col max-sm:gap-0">
              <div class="min-w-0">
                <div class="font-sans font-bold text-ink [overflow-wrap:anywhere]">{{ message.title }}</div>
                <div class="text-sm text-muted tabular-nums">
                  {{ sentAt(message) }} &middot; {{ typeLabel(message.type) }} &middot;
                  <template v-if="message.recipientCount > 0">{{ message.recipientCount }} {{ message.recipientCount === 1 ? 'recipient' : 'recipients' }}</template>
                  <template v-else>No deliveries recorded</template>
                </div>
                <div v-if="summaryParts(message).length" class="mt-1.5 flex flex-wrap gap-1.5">
                  <StatusBadge v-for="part in summaryParts(message)" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
                </div>
              </div>
              <TextButton class="shrink-0 max-sm:text-left" @click="openDeliveries(message)">
                View deliveries<span class="sr-only"> for {{ message.title }}</span>
              </TextButton>
            </li>
          </ul>

          <!-- lg and up: the sent messages in a bordered card (grey header row, hairline between rows, footer line) -->
          <div v-if="messages.length" class="hidden overflow-x-auto rounded-md border border-rule bg-paper lg:block">
            <div class="min-w-[52rem]">
              <table :class="TABLE_FROM_LG">
                <caption class="sr-only">Sent messages, newest first</caption>
                <thead>
                  <tr class="border-b border-rule">
                    <th scope="col" :class="CARD_TH">Subject</th>
                    <th scope="col" :class="CARD_TH">Sent to</th>
                    <th scope="col" :class="CARD_TH">Sent</th>
                    <th scope="col" :class="CARD_TH">Delivery</th>
                    <th scope="col" :class="CARD_TH"><span class="sr-only">Deliveries</span></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="message in messages" :key="message.id" class="border-b border-rule align-top">
                    <td :class="[CARD_TD, 'max-w-0 w-[34%] font-medium text-ink [overflow-wrap:anywhere]']">{{ message.title }}</td>
                    <td :class="CARD_TD">
                      {{ typeLabel(message.type) }}
                      <div class="text-sm text-muted">
                        <template v-if="message.recipientCount > 0">{{ message.recipientCount }} {{ message.recipientCount === 1 ? 'recipient' : 'recipients' }}</template>
                        <template v-else>No deliveries recorded</template>
                      </div>
                    </td>
                    <td :class="[CARD_TD, 'whitespace-nowrap text-muted']">{{ sentAt(message) }}</td>
                    <td :class="CARD_TD">
                      <div v-if="summaryParts(message).length" class="flex flex-wrap gap-1.5">
                        <StatusBadge v-for="part in summaryParts(message)" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
                      </div>
                      <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">No delivery counts</span></span>
                    </td>
                    <td :class="[CARD_TD, 'whitespace-nowrap text-right']">
                      <TextButton @click="openDeliveries(message)">
                        View deliveries<span class="sr-only"> for {{ message.title }}</span>
                      </TextButton>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="flex flex-wrap justify-between gap-x-4 gap-y-2 px-4 py-3 text-sm text-muted">
                <span>{{ messages.length }} {{ messages.length === 1 ? 'message' : 'messages' }} sent so far</span>
                <span>Newest first</span>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <!-- Confirm before email goes out -->
    <ConfirmDialog
      v-model="confirmOpen"
      :title="`Send to ${countLabel}?`"
      :message="confirmMessage"
      :confirm-label="`Send to ${countLabel}`"
      :busy="sending"
      @confirm="sendMessage"
    />

    <!-- Deliveries -->
    <BaseModal v-model="deliveriesOpen" :title="selectedMessage ? `Deliveries: ${selectedMessage.title}` : 'Deliveries'" size="lg">
      <p v-if="selectedMessage" class="mt-0 mb-3 text-base text-muted tabular-nums">Sent {{ sentAt(selectedMessage) }}</p>
      <p v-if="deliveriesLoading" class="m-0 py-4 text-muted" role="status">Loading deliveries...</p>
      <AlertBanner v-else-if="deliveriesError">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span>The deliveries did not load. Check your connection and try again.</span>
          <BaseButton variant="secondary" size="sm" @click="loadDeliveries">Try again</BaseButton>
        </div>
      </AlertBanner>
      <EmptyNote v-else-if="!deliveries.length">No deliveries were recorded for this message.</EmptyNote>
      <template v-else>
        <div class="mb-3 flex flex-wrap gap-1.5" aria-label="Delivery totals" role="group">
          <StatusBadge v-for="part in deliveryTotals" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
        </div>
        <ul class="m-0 list-none border-t border-rule p-0">
          <li v-for="delivery in orderedDeliveries" :key="delivery.id" class="flex items-start justify-between gap-3 border-b border-rule py-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-baseline gap-x-4">
                <span class="font-medium [overflow-wrap:anywhere]">{{ delivery.recipient?.name || 'Unknown' }}</span>
                <StatusLabel :tone="statusOf(delivery).tone">{{ statusOf(delivery).label }}</StatusLabel>
                <span v-if="delivery.deliveryTime" class="text-sm text-muted tabular-nums">{{ formatDate(delivery.deliveryTime, 'MMM d, yyyy, h:mm a') }}</span>
              </div>
              <div class="text-sm text-muted [overflow-wrap:anywhere]">{{ delivery.recipient?.email }}</div>
              <div v-if="delivery.responseNotes || delivery.attempts > 0" class="text-sm text-muted [overflow-wrap:anywhere]">
                <span v-if="delivery.responseNotes">{{ friendlyNotes(delivery.responseNotes) }}</span>
                <span v-if="delivery.attempts > 0" class="tabular-nums"><template v-if="delivery.responseNotes"> &middot; </template>{{ attemptsLabel(delivery.attempts) }}</span>
              </div>
            </div>
            <BaseButton
              v-if="canRetry(delivery)"
              variant="secondary"
              size="sm"
              class="shrink-0"
              :disabled="retryingIds.includes(delivery.id)"
              :aria-busy="retryingIds.includes(delivery.id) ? 'true' : undefined"
              @click="retryDelivery(delivery)"
            >
              {{ retryingIds.includes(delivery.id) ? 'Retrying...' : 'Retry' }}<span class="sr-only"> for {{ delivery.recipient?.name }}</span>
            </BaseButton>
          </li>
        </ul>
      </template>
      <template #footer>
        <BaseButton variant="secondary" @click="deliveriesOpen = false">Close</BaseButton>
      </template>
    </BaseModal>
    </div>
  </div>
</template>

<script>
import api from '@/services/api'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { formatDate, localISODate } from '@/utils'
import { personLabel, previewRecipients, previewSummary, sendableCount, skippedNote, skippedSentence } from '@/utils/audiencePreview'
import { buildCommunicationRequest } from '@/utils/communicationPayload'
import { attemptsLabel, friendlyNotes, countDeliveries, deliveryStatus, failedFirst, deliverySummaryParts, sortMessages, typeLabel } from '@/utils/messageHistory'
import { countsForDues } from '@/utils/memberStatus'
import { monthsBehind } from '@/utils/dues'
import { paidMonthsByMember } from '@/utils/yearStrip'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import PageHead from '@/components/PageHead.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'

import { CARD, TABLE_FROM_LG, TABLE_CARD_TH as CARD_TH, TABLE_CARD_TD as CARD_TD } from '@/ui/classes'
const MESSAGE_MAX = 5000
const SUBJECT_MAX = 200
const EMPTY_ERRORS = { monthsOverdue: '', memberId: '', subject: '', message: '' }
// The server names the request fields title and messageContent; the form calls them subject and message
const SERVER_FIELDS = { title: 'subject', messageContent: 'message' }

const emptyForm = () => ({ recipientType: 'ALL', memberId: '', monthsOverdue: '1', subject: '', message: '' })

export default {
  name: 'CommunicationsView',
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, ConfirmDialog, EmptyNote, PageHead, SectionTitle, StatusBadge, StatusLabel, TextButton, YearStrip },
  setup() {
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      formatDate,
      monthsBehind,
      personLabel,
      typeLabel,
      attemptsLabel,
      friendlyNotes,
      CARD,
      TABLE_FROM_LG,
      CARD_TH,
      CARD_TD,
      MESSAGE_MAX
    }
  },
  data() {
    return {
      members: [],
      paidByMember: null,
      today: localISODate(),
      communications: [],
      loaded: false,
      loadError: false,
      form: emptyForm(),
      formErrors: { ...EMPTY_ERRORS },
      sendError: '',
      sending: false,
      confirmOpen: false,
      selectedMessage: null,
      deliveriesOpen: false,
      deliveries: [],
      deliveriesLoading: false,
      deliveriesError: false,
      retryingIds: []
    }
  },
  computed: {
    activeMembers() {
      return this.members.filter(countsForDues).sort((a, b) => a.name.localeCompare(b.name))
    },
    messages() {
      return sortMessages(this.communications)
    },
    recipients() {
      const { recipientType, monthsOverdue, memberId } = this.form
      return previewRecipients(this.members, recipientType, monthsOverdue, memberId)
    },
    recipientTotal() {
      return sendableCount(this.recipients)
    },
    skippedNote() {
      return skippedNote(this.recipients)
    },
    countLabel() {
      return personLabel(this.recipientTotal)
    },
    summaryText() {
      return previewSummary(this.recipients, this.form.recipientType, this.form.monthsOverdue)
    },
    confirmMessage() {
      const skipped = skippedSentence(this.recipients)
      return `“${this.form.subject.trim()}” goes out by email. You cannot take it back.${skipped ? ` ${skipped}` : ''}`
    },
    orderedDeliveries() {
      return failedFirst(this.deliveries)
    },
    deliveryTotals() {
      return deliverySummaryParts(countDeliveries(this.deliveries))
    }
  },
  async created() {
    await this.loadData()
  },
  methods: {
    // Members are only needed to compose and count recipients (STAFF and above)
    async loadData() {
      try {
        const [members, communications] = await Promise.all([
          this.authStore.isStaff ? api.getMembers() : [],
          api.getCommunications(),
          this.authStore.isStaff ? this.loadPayments() : null
        ])
        this.members = Array.isArray(members) ? members : []
        this.communications = Array.isArray(communications) ? communications : []
        this.loadError = false
      } catch (error) {
        console.error('Error loading messages:', error)
        this.members = []
        this.communications = []
        this.loadError = true
      } finally {
        this.loaded = true
      }
    },
    // The paid months behind the strips: one existing call, grouped by member. A failure only hides the strips.
    async loadPayments() {
      try {
        this.paidByMember = paidMonthsByMember(await api.getPayments())
      } catch (error) {
        console.error('Error loading payments for the year strips:', error)
        this.paidByMember = null
      }
    },
    stripProps(member) {
      return {
        joinDate: member.joinDate || '',
        paidMonths: this.paidByMember.get(member.id) || new Set(),
        currentMonth: this.today.slice(0, 7),
        monthsMissed: member.consecutiveMonthsMissed || 0,
        countsForDues: countsForDues(member),
        label: `Dues for ${member.name}, last 12 months`
      }
    },
    async refreshMessages() {
      try {
        const communications = await api.getCommunications()
        this.communications = Array.isArray(communications) ? communications : []
      } catch (error) {
        console.error('Error refreshing messages:', error)
      }
    },
    sentAt(message) {
      return formatDate(message.sentDate || message.createdDate, 'MMM d, yyyy, h:mm a')
    },
    summaryParts(message) {
      return deliverySummaryParts(message.deliverySummary)
    },
    hasEmail(memberId) {
      const member = this.members.find(m => String(m.id) === String(memberId))
      return !!(member && member.email)
    },
    statusOf(delivery) {
      return deliveryStatus(delivery.status)
    },
    validateForm() {
      const errors = { ...EMPTY_ERRORS }
      const f = this.form
      if (f.recipientType === 'OVERDUE' && !(Number(f.monthsOverdue) >= 1)) errors.monthsOverdue = 'Enter 1 or more months.'
      if (f.recipientType === 'SPECIFIC' && !f.memberId) errors.memberId = 'Choose a member.'
      else if (f.recipientType === 'SPECIFIC' && !this.hasEmail(f.memberId)) errors.memberId = 'This member has no email address.'
      if (!f.subject.trim()) errors.subject = 'Enter a subject.'
      else if (f.subject.trim().length > SUBJECT_MAX) errors.subject = `The subject can be up to ${SUBJECT_MAX} characters.`
      if (!f.message.trim()) errors.message = 'Write a message.'
      else if (f.message.trim().length > MESSAGE_MAX) errors.message = `The message can be up to ${MESSAGE_MAX} characters.`
      this.formErrors = errors
      return !Object.values(errors).some(Boolean)
    },
    // Check the form, then ask before email goes out
    askToSend() {
      this.sendError = ''
      if (!this.validateForm()) return
      if (this.recipientTotal === 0) {
        this.sendError = 'There is nobody to send this to.'
        return
      }
      this.confirmOpen = true
    },
    async sendMessage() {
      const payload = buildCommunicationRequest(this.form)
      const { recipientType, memberId, monthsOverdue } = this.form
      const count = this.recipientTotal
      this.sending = true
      try {
        if (recipientType === 'ALL') await api.sendToAllMembers(payload)
        else if (recipientType === 'OVERDUE') await api.sendToOverdueMembers(Number(monthsOverdue), payload)
        else await api.sendToMember(memberId, payload)
        this.confirmOpen = false
        this.form = emptyForm()
        this.formErrors = { ...EMPTY_ERRORS }
        this.notify('success', 'Sending started', `${personLabel(count)} will get it in the next few minutes. Check the delivery status below.`)
        await this.refreshMessages()
      } catch (error) {
        console.error('Error sending message:', error)
        this.confirmOpen = false
        this.showSendError(error)
      } finally {
        this.sending = false
      }
    },
    // A field error goes under its field; anything else goes in the banner of the card
    showSendError(error) {
      const fieldErrors = Array.isArray(error.fieldErrors) ? error.fieldErrors : []
      const rest = []
      for (const { field, message } of fieldErrors) {
        const key = SERVER_FIELDS[field] || field
        if (key in EMPTY_ERRORS && !this.formErrors[key]) this.formErrors[key] = message
        else rest.push(message)
      }
      if (!fieldErrors.length) rest.push(error.message || 'The message was not sent. Try again.')
      if (rest.length) {
        this.sendError = rest.join(' ')
        this.notifyFailure('Could not send message', error)
      }
    },
    async openDeliveries(message) {
      this.selectedMessage = message
      this.deliveries = []
      this.deliveriesOpen = true
      await this.loadDeliveries()
    },
    async loadDeliveries() {
      this.deliveriesLoading = true
      this.deliveriesError = false
      try {
        const deliveries = await api.getCommunicationDeliveries(this.selectedMessage.id)
        this.deliveries = Array.isArray(deliveries) ? deliveries : []
      } catch (error) {
        console.error('Error loading deliveries:', error)
        this.deliveries = []
        this.deliveriesError = true
      } finally {
        this.deliveriesLoading = false
      }
    },
    // Only a failed email can be sent again, and only by STAFF and above
    canRetry(delivery) {
      return this.authStore.isStaff && delivery.status === 'FAILED' && delivery.channel === 'EMAIL'
    },
    async retryDelivery(delivery) {
      this.retryingIds.push(delivery.id)
      try {
        const updated = await api.retryDelivery(this.selectedMessage.id, delivery.id)
        this.deliveries = this.deliveries.map(item => (item.id === updated.id ? updated : item))
        if (updated.status === 'FAILED') this.notify('warning', 'Retry failed', `The email to ${delivery.recipient?.name || 'the member'} still did not go out. Check the address and try again later.`)
        else this.notify('success', 'Delivery retried', delivery.recipient?.name || '')
        await this.refreshMessages()
      } catch (error) {
        console.error('Error retrying delivery:', error)
        this.notifyFailure('Could not retry delivery', error)
      } finally {
        this.retryingIds = this.retryingIds.filter(id => id !== delivery.id)
      }
    },
    notify(type, title, message) {
      this.appStore.addNotification({ type, title, message, isToast: true })
    },
    // The shared API handler already shows an "Access Denied" toast for 403
    notifyFailure(title, error) {
      if (error.response?.status === 403) return
      this.notify('error', title, error.message || 'Request failed')
    }
  }
}
</script>

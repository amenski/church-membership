<template>
  <!-- From lg the header is a full-width band, so the page's own padding (App.vue) is dropped here and the content area below carries it -->
  <div class="lg:max-w-none! lg:p-0!">
    <PageHead :title="$t('nav.messages')" :lead="$t('messages.lead')" band />

    <div class="lg:mx-auto lg:max-w-[1400px] lg:px-8 lg:pt-6 lg:pb-10">
    <AlertBanner v-if="loadError">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <span>{{ $t('messages.loadError') }}</span>
        <BaseButton variant="secondary" size="sm" @click="loadData">{{ $t('common.tryAgain') }}</BaseButton>
      </div>
    </AlertBanner>

    <div>
      <!-- Compose and who gets it (STAFF and above): side by side from lg, stacked below -->
      <div v-if="authStore.isStaff" class="mb-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,28rem)] lg:items-start">
        <section :class="CARD" aria-labelledby="compose-title">
          <SectionTitle id="compose-title">{{ $t('messages.newMessage') }}</SectionTitle>
          <AlertBanner v-if="sendError">{{ sendError }}</AlertBanner>
          <form class="flex flex-col gap-4" novalidate @submit.prevent="askToSend">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <BaseSelect id="message-audience" v-model="form.recipientType" :label="$t('messages.sendTo')">
                <option value="ALL">{{ $t('messages.everyone') }}</option>
                <option value="OVERDUE">{{ $t('messages.behindOnDues') }}</option>
                <option value="SPECIFIC">{{ $t('messages.oneMember') }}</option>
              </BaseSelect>
              <BaseInput
                v-if="form.recipientType === 'OVERDUE'"
                id="message-months"
                v-model="form.monthsOverdue"
                :label="$t('messages.atLeastMonths')"
                type="number"
                min="1"
                step="1"
                inputmode="numeric"
                :error="formErrors.monthsOverdue"
              />
              <MemberPicker v-else-if="form.recipientType === 'SPECIFIC'" id="message-member" v-model="form.memberId" :label="$t('payments.member')" :members="activeMembers" :paid-by-member="paidByMember" :current-month="today.slice(0, 7)" :error="formErrors.memberId" />
            </div>
            <BaseInput id="message-subject" v-model="form.subject" :label="$t('messages.subject')" maxlength="200" autocomplete="off" :error="formErrors.subject" />
            <BaseTextarea
              id="message-body"
              v-model="form.message"
              :label="$t('messages.message')"
              :rows="6"
              :max="MESSAGE_MAX"
              :hint="$t('messages.bodyHint', { token: $t('messages.nameToken') })"
              :error="formErrors.message"
            />
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
              <BaseButton type="submit" :disabled="sending" :aria-busy="sending ? 'true' : undefined" class="max-sm:w-full">
                {{ sending ? $t('messages.sending') : $t('messages.sendToCount', { label: countLabel }) }}
              </BaseButton>
              <span v-if="skippedNote" class="text-sm text-ochre-text">{{ skippedNote }}</span>
            </div>
          </form>
        </section>

        <!-- Who gets this: computed from the members and payments already loaded -->
        <section :class="CARD" aria-labelledby="who-title">
          <SectionTitle id="who-title">{{ $t('messages.whoGetsThis') }}</SectionTitle>
          <p class="mt-0 mb-3 text-sm text-muted" role="status">{{ summaryText }}</p>
          <ul v-if="recipients.length" class="m-0 max-h-[32rem] list-none overflow-y-auto border-t border-rule p-0">
            <li v-for="row in recipients" :key="row.member.id" class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 border-b border-rule py-2.5">
              <div class="min-w-0">
                <div class="font-medium text-ink [overflow-wrap:anywhere]">{{ row.member.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ row.member.consecutiveMonthsMissed > 0 ? monthsBehind(row.member.consecutiveMonthsMissed, $t) : $t('dues.badgePaid') }}</div>
              </div>
              <YearStrip v-if="paidByMember" v-bind="stripProps(row.member)" />
              <div class="flex basis-full flex-wrap items-center gap-x-2 gap-y-1">
                <StatusBadge v-if="row.state === 'send'" tone="paid">{{ $t('messages.willGet') }}</StatusBadge>
                <StatusBadge v-else-if="row.state === 'noEmail'" tone="behind">{{ $t('messages.skippedNoEmail') }}</StatusBadge>
                <StatusBadge v-else tone="muted">{{ $t('messages.skippedShared') }}</StatusBadge>
                <span v-if="row.state === 'send'" class="text-xs text-muted [overflow-wrap:anywhere]">{{ row.member.email }}</span>
                <span v-else-if="row.state === 'shared'" class="text-xs text-muted">{{ $t('messages.getsOneCopy', { name: row.sharesWith }) }}</span>
                <span v-else-if="row.member.phone" class="text-xs text-muted">
                  <a :href="`tel:${row.member.phone.replace(/[^+\d]/g, '')}`" class="text-teal underline underline-offset-[3px] hover:text-teal-hover">{{ $t('messages.callInstead', { phone: row.member.phone }) }}<span class="sr-only">{{ $t('messages.callForSr', { name: row.member.name }) }}</span></a>
                </span>
                <span v-else class="text-xs text-muted">{{ $t('messages.noPhoneOnFile') }}</span>
              </div>
            </li>
          </ul>
          <p class="mt-3 mb-0 text-sm text-muted">{{ $t('messages.neverSentNoEmail') }}</p>
        </section>
      </div>

      <!-- History -->
      <section aria-labelledby="history-title">
        <SectionTitle id="history-title">{{ $t('messages.sentMessages') }}</SectionTitle>

        <p v-if="!loaded" class="m-0 py-4 text-(length:--text-body) text-muted" role="status">{{ $t('messages.loading') }}</p>

        <template v-else-if="!loadError">
          <div v-if="!messages.length">
            <EmptyNote>{{ $t('messages.noMessages') }}<template v-if="authStore.isStaff">{{ $t('messages.noMessagesStaff') }}</template></EmptyNote>
          </div>

          <!-- below lg: one row a message -->
          <ul v-if="messages.length" class="m-0 list-none border-t border-rule p-0 lg:hidden">
            <li v-for="message in pagedMessages" :key="message.id" class="flex items-start justify-between gap-4 border-b border-rule py-3 text-(length:--text-body) max-sm:flex-col max-sm:gap-0">
              <div class="min-w-0">
                <div class="font-sans font-bold text-ink [overflow-wrap:anywhere]">{{ message.title }}</div>
                <div class="text-sm text-muted tabular-nums">
                  {{ sentAt(message) }} &middot; {{ $t(typeKey(message.type)) }} &middot;
                  <template v-if="message.recipientCount > 0">{{ $t('messages.recipientCount', message.recipientCount) }}</template>
                  <template v-else>{{ $t('messages.noDeliveriesRecorded') }}</template>
                </div>
                <div v-if="summaryParts(message).length" class="mt-1.5 flex flex-wrap gap-1.5">
                  <StatusBadge v-for="part in summaryParts(message)" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
                </div>
              </div>
              <TextButton class="shrink-0 max-sm:text-left" @click="openDeliveries(message)">
                {{ $t('messages.viewDeliveries') }}<span class="sr-only">{{ $t('messages.viewDeliveriesSr', { title: message.title }) }}</span>
              </TextButton>
            </li>
          </ul>
          <Pager v-if="messages.length" v-bind="pagerProps" class="mt-4 lg:hidden" @update:page="setPage" @update:page-size="setPageSize" />

          <!-- lg and up: the sent messages in a bordered card (grey header row, hairline between rows, footer line) -->
          <div v-if="messages.length" class="hidden overflow-x-auto rounded-md border border-rule bg-paper lg:block">
            <div class="min-w-[52rem]">
              <table :class="TABLE_FROM_LG">
                <caption class="sr-only">{{ $t('messages.sentCaption') }}</caption>
                <thead>
                  <tr class="border-b border-rule">
                    <th scope="col" :class="CARD_TH">{{ $t('messages.colSubject') }}</th>
                    <th scope="col" :class="CARD_TH">{{ $t('messages.colSentTo') }}</th>
                    <th scope="col" :class="CARD_TH">{{ $t('messages.colSent') }}</th>
                    <th scope="col" :class="CARD_TH">{{ $t('messages.colDelivery') }}</th>
                    <th scope="col" :class="CARD_TH"><span class="sr-only">{{ $t('messages.colDeliveries') }}</span></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="message in pagedMessages" :key="message.id" class="border-b border-rule align-top">
                    <td :class="[CARD_TD, 'max-w-0 w-[34%] font-medium text-ink [overflow-wrap:anywhere]']">{{ message.title }}</td>
                    <td :class="CARD_TD">
                      {{ $t(typeKey(message.type)) }}
                      <div class="text-sm text-muted">
                        <template v-if="message.recipientCount > 0">{{ $t('messages.recipientCount', message.recipientCount) }}</template>
                        <template v-else>{{ $t('messages.noDeliveriesRecorded') }}</template>
                      </div>
                    </td>
                    <td :class="[CARD_TD, 'whitespace-nowrap text-muted']">{{ sentAt(message) }}</td>
                    <td :class="CARD_TD">
                      <div v-if="summaryParts(message).length" class="flex flex-wrap gap-1.5">
                        <StatusBadge v-for="part in summaryParts(message)" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
                      </div>
                      <span v-else class="text-muted"><span aria-hidden="true">&ndash;</span><span class="sr-only">{{ $t('messages.noDeliveryCounts') }}</span></span>
                    </td>
                    <td :class="[CARD_TD, 'whitespace-nowrap text-right']">
                      <TextButton @click="openDeliveries(message)">
                        {{ $t('messages.viewDeliveries') }}<span class="sr-only">{{ $t('messages.viewDeliveriesSr', { title: message.title }) }}</span>
                      </TextButton>
                    </td>
                  </tr>
                </tbody>
              </table>
              <Pager v-bind="pagerProps" class="px-4 py-3" @update:page="setPage" @update:page-size="setPageSize" />
              <div :class="['flex flex-wrap justify-between gap-x-4 gap-y-2 px-4 text-sm text-muted', pagerShown ? 'pb-3' : 'py-3']">
                <span v-if="!pagerShown">{{ $t('messages.sentSoFar', messages.length) }}</span>
                <span class="ml-auto">{{ $t('messages.newestFirst') }}</span>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <!-- Confirm before email goes out -->
    <ConfirmDialog
      v-model="confirmOpen"
      :title="$t('messages.confirmTitle', { label: countLabel })"
      :message="confirmMessage"
      :confirm-label="$t('messages.sendToCount', { label: countLabel })"
      :busy="sending"
      @confirm="sendMessage"
    />

    <!-- Deliveries -->
    <BaseModal v-model="deliveriesOpen" :title="selectedMessage ? $t('messages.deliveriesTitle', { title: selectedMessage.title }) : $t('messages.deliveriesTitlePlain')" size="lg">
      <p v-if="selectedMessage" class="mt-0 mb-3 text-base text-muted tabular-nums">{{ $t('messages.sentAtLine', { date: sentAt(selectedMessage) }) }}</p>
      <p v-if="deliveriesLoading" class="m-0 py-4 text-muted" role="status">{{ $t('messages.loadingDeliveries') }}</p>
      <AlertBanner v-else-if="deliveriesError">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span>{{ $t('messages.deliveriesLoadError') }}</span>
          <BaseButton variant="secondary" size="sm" @click="loadDeliveries">{{ $t('common.tryAgain') }}</BaseButton>
        </div>
      </AlertBanner>
      <EmptyNote v-else-if="!deliveries.length">{{ $t('messages.noDeliveries') }}</EmptyNote>
      <template v-else>
        <div class="mb-3 flex flex-wrap gap-1.5" :aria-label="$t('messages.deliveryTotalsAria')" role="group">
          <StatusBadge v-for="part in deliveryTotals" :key="part.key" :tone="part.tone">{{ part.text }}</StatusBadge>
        </div>
        <ul class="m-0 list-none border-t border-rule p-0">
          <li v-for="delivery in pagedDeliveries" :key="delivery.id" class="flex items-start justify-between gap-3 border-b border-rule py-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-baseline gap-x-4">
                <span class="font-medium [overflow-wrap:anywhere]">{{ delivery.recipient?.name || $t('payments.unknown') }}</span>
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
              {{ retryingIds.includes(delivery.id) ? $t('messages.retrying') : $t('messages.retry') }}<span class="sr-only">{{ $t('messages.retrySr', { name: delivery.recipient?.name }) }}</span>
            </BaseButton>
          </li>
        </ul>
        <Pager :page="deliveryPage" :page-size="deliveryPageSize" :total="orderedDeliveries.length" compact class="mt-3" @update:page="deliveryPage = $event" @update:page-size="setDeliveryPageSize" />
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
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/authStore'
import { useAppStore } from '../stores/appStore'
import { formatDate, localISODate } from '@/utils'
import { personLabel, previewRecipients, previewSummary, sendableCount, skippedNote, skippedSentence } from '@/utils/audiencePreview'
import { buildCommunicationRequest } from '@/utils/communicationPayload'
import { attemptsLabel, friendlyNotes, countDeliveries, deliveryStatus, failedFirst, deliverySummaryParts, sortMessages, typeKey } from '@/utils/messageHistory'
import { countsForDues } from '@/utils/memberStatus'
import { monthsBehind } from '@/utils/dues'
import { paidMonthsFromMap } from '@/utils/yearStrip'
import { PAGE_SIZES, clampPage, pageSlice } from '@/utils/paging'
import { queryPaging } from '@/utils/queryPaging'
import AlertBanner from '@/components/AlertBanner.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseInput from '@/components/BaseInput.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseSelect from '@/components/BaseSelect.vue'
import BaseTextarea from '@/components/BaseTextarea.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EmptyNote from '@/components/EmptyNote.vue'
import MemberPicker from '@/components/MemberPicker.vue'
import PageHead from '@/components/PageHead.vue'
import Pager from '@/components/Pager.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import StatusLabel from '@/components/StatusLabel.vue'
import TextButton from '@/components/TextButton.vue'
import YearStrip from '@/components/YearStrip.vue'

import { CARD, TABLE_FROM_LG, TABLE_CARD_TH as CARD_TH, TABLE_CARD_TD as CARD_TD } from '@/ui/classes'
// Sent messages: 10 a page; the deliveries of one message in its dialog: 25 a page (no URL state there)
const MESSAGES_PER_PAGE = 10
const DELIVERIES_PER_PAGE = 25
const MESSAGE_MAX = 5000
const SUBJECT_MAX = 200
const EMPTY_ERRORS = { monthsOverdue: '', memberId: '', subject: '', message: '' }
// The server names the request fields title and messageContent; the form calls them subject and message
const SERVER_FIELDS = { title: 'subject', messageContent: 'message' }

const emptyForm = () => ({ recipientType: 'ALL', memberId: '', monthsOverdue: '1', subject: '', message: '' })

export default {
  name: 'CommunicationsView',
  mixins: [queryPaging({ defaultSize: MESSAGES_PER_PAGE })],
  components: { AlertBanner, BaseButton, BaseInput, BaseModal, BaseSelect, BaseTextarea, ConfirmDialog, EmptyNote, MemberPicker, PageHead, Pager, SectionTitle, StatusBadge, StatusLabel, TextButton, YearStrip },
  setup() {
    const { t } = useI18n()
    return {
      authStore: useAuthStore(),
      appStore: useAppStore(),
      formatDate,
      monthsBehind,
      personLabel,
      typeKey,
      attemptsLabel,
      friendlyNotes,
      t,
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
      deliveryPage: 1,
      deliveryPageSize: DELIVERIES_PER_PAGE,
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
    currentPage() {
      return clampPage(this.page, this.messages.length, this.pageSize)
    },
    // the rows on screen: one page of the sent messages
    pagedMessages() {
      return pageSlice(this.messages, this.currentPage, this.pageSize)
    },
    pagerProps() {
      return { page: this.currentPage, pageSize: this.pageSize, total: this.messages.length }
    },
    pagerShown() {
      return this.messages.length > Math.min(...PAGE_SIZES)
    },
    recipients() {
      const { recipientType, monthsOverdue, memberId } = this.form
      return previewRecipients(this.members, recipientType, monthsOverdue, memberId)
    },
    recipientTotal() {
      return sendableCount(this.recipients)
    },
    skippedNote() {
      return skippedNote(this.recipients, this.t)
    },
    countLabel() {
      return personLabel(this.recipientTotal, this.t)
    },
    summaryText() {
      return previewSummary(this.recipients, this.form.recipientType, this.form.monthsOverdue, this.t)
    },
    confirmMessage() {
      const skipped = skippedSentence(this.recipients, this.t)
      const body = this.$t('messages.confirmBody', { subject: this.form.subject.trim() })
      return skipped ? `${body} ${skipped}` : body
    },
    orderedDeliveries() {
      return failedFirst(this.deliveries)
    },
    pagedDeliveries() {
      return pageSlice(this.orderedDeliveries, this.deliveryPage, this.deliveryPageSize)
    },
    deliveryTotals() {
      return deliverySummaryParts(countDeliveries(this.deliveries))
    }
  },
  watch: {
    // the list changed length (loaded, a message sent): a page beyond the last becomes the last
    'messages.length'(length) {
      if (this.loaded) this.settlePage(length)
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
    // The paid months behind the strips: GET /payments/paid-months, grouped by member. A failure only hides the strips.
    async loadPayments() {
      try {
        this.paidByMember = paidMonthsFromMap(await api.getPaidMonths(12))
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
        label: this.$t('strip.duesFor', { name: member.name })
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
      return deliverySummaryParts(message.deliverySummary, this.t)
    },
    hasEmail(memberId) {
      const member = this.members.find(m => String(m.id) === String(memberId))
      return !!(member && member.email)
    },
    statusOf(delivery) {
      return deliveryStatus(delivery.status, this.t)
    },
    validateForm() {
      const errors = { ...EMPTY_ERRORS }
      const f = this.form
      if (f.recipientType === 'OVERDUE' && !(Number(f.monthsOverdue) >= 1)) errors.monthsOverdue = this.$t('messages.vMonths')
      if (f.recipientType === 'SPECIFIC' && !f.memberId) errors.memberId = this.$t('messages.vChooseMember')
      else if (f.recipientType === 'SPECIFIC' && !this.hasEmail(f.memberId)) errors.memberId = this.$t('messages.vNoEmail')
      if (!f.subject.trim()) errors.subject = this.$t('messages.vSubject')
      else if (f.subject.trim().length > SUBJECT_MAX) errors.subject = this.$t('messages.vSubjectLong', { n: SUBJECT_MAX })
      if (!f.message.trim()) errors.message = this.$t('messages.vMessage')
      else if (f.message.trim().length > MESSAGE_MAX) errors.message = this.$t('messages.vMessageLong', { n: MESSAGE_MAX })
      this.formErrors = errors
      return !Object.values(errors).some(Boolean)
    },
    // Check the form, then ask before email goes out
    askToSend() {
      this.sendError = ''
      if (!this.validateForm()) return
      if (this.recipientTotal === 0) {
        this.sendError = this.$t('messages.nobodyToSend')
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
        this.notify('success', this.$t('messages.sendingStarted'), this.$t('messages.sendingStartedMessage', { who: personLabel(count, this.t) }))
        await this.refreshMessages()
        // the new message is the first row of the newest-first list
        this.resetPage()
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
      if (!fieldErrors.length) rest.push(error.message || this.$t('messages.notSent'))
      if (rest.length) {
        this.sendError = rest.join(' ')
        this.notifyFailure(this.$t('messages.couldNotSend'), error)
      }
    },
    async openDeliveries(message) {
      this.selectedMessage = message
      this.deliveries = []
      this.deliveryPage = 1
      this.deliveriesOpen = true
      await this.loadDeliveries()
    },
    setDeliveryPageSize(size) {
      this.deliveryPageSize = size
      this.deliveryPage = 1
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
        if (updated.status === 'FAILED') this.notify('warning', this.$t('messages.retryFailed'), this.$t('messages.retryFailedMessage', { name: delivery.recipient?.name || this.$t('payments.memberFallback') }))
        else this.notify('success', this.$t('messages.deliveryRetried'), delivery.recipient?.name || '')
        await this.refreshMessages()
      } catch (error) {
        console.error('Error retrying delivery:', error)
        this.notifyFailure(this.$t('messages.couldNotRetry'), error)
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
      this.notify('error', title, error.message || this.$t('common.requestFailed'))
    }
  }
}
</script>

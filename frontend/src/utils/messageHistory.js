import { formatDate } from '@/utils'

// The words live in locales/ (messages.*)
const TYPE_KEYS = { REMINDER: 'messages.typeReminder', ANNOUNCEMENT: 'messages.typeAnnouncement', PERSONAL: 'messages.typePersonal' }

/** The i18n key of a message type's name; an unknown type is shown as it came. */
export function typeKey(type) {
  return TYPE_KEYS[type] || type || ''
}

/**
 * "1 attempt", "3 attempts"; empty when none were made (or the count is unknown).
 * @param {number} n
 * @param {Function} t useI18n's t
 */
export function attemptsLabel(n, t) {
  return n > 0 ? t('messages.attempts', n) : ''
}

/**
 * The delivery counts of one message as words with a tone for StatusBadge: "8 delivered" (sent plus
 * delivered, both mean the mail left), "1 failed", "1 pending". Zero counts are left out.
 * @param {object} summary
 * @param {Function} t useI18n's t
 * @returns {{ key: string, text: string, tone: 'paid'|'danger'|'behind' }[]}
 */
export function deliverySummaryParts(summary, t) {
  const { sent = 0, delivered = 0, failed = 0, pending = 0 } = summary || {}
  const parts = [
    { key: 'delivered', count: sent + delivered, text: t('messages.summaryDelivered', { n: sent + delivered }), tone: 'paid' },
    { key: 'failed', count: failed, text: t('messages.summaryFailed', { n: failed }), tone: 'danger' },
    { key: 'pending', count: pending, text: t('messages.summaryPending', { n: pending }), tone: 'behind' }
  ]
  return parts.filter(part => part.count > 0)
}

/**
 * StatusLabel tone and word for one delivery status. SENT reads Delivered: the mail left, as the
 * totals say.
 * @param {string} status
 * @param {Function} t useI18n's t
 */
export function deliveryStatus(status, t) {
  switch (status) {
    case 'SENT':
    case 'DELIVERED': return { tone: 'paid', label: t('messages.delivered') }
    case 'FAILED': return { tone: 'danger', label: t('messages.failed') }
    case 'PENDING': return { tone: 'behind', label: t('messages.pending') }
    default: return { tone: 'behind', label: status || '' }
  }
}

/** Counts of one list of deliveries, in the shape of a list item's deliverySummary. */
export function countDeliveries(deliveries) {
  const count = status => deliveries.filter(delivery => delivery.status === status).length
  return { sent: count('SENT'), delivered: count('DELIVERED'), failed: count('FAILED'), pending: count('PENDING') }
}

/** Newest first by sent date, then created date, then id. Does not change the input. */
export function sortMessages(messages) {
  const when = message => String(message.sentDate || message.createdDate || '')
  return [...messages].sort((a, b) => when(b).localeCompare(when(a)) || b.id - a.id)
}

/** Delivery notes carry the server's raw ISO timestamp; show it as a readable time. */
export function friendlyNotes(notes) {
  return (notes || '').replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?/g, iso => formatDate(iso, 'MMM d, yyyy, h:mm a') || iso)
}

/** Failed deliveries first, so the ones to act on are at the top; the others keep their order. Does not change the input. */
export function failedFirst(deliveries) {
  const failed = deliveries.filter(delivery => delivery.status === 'FAILED')
  return [...failed, ...deliveries.filter(delivery => delivery.status !== 'FAILED')]
}

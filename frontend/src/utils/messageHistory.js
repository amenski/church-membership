import { formatDate } from '@/utils'

const TYPE_LABELS = { REMINDER: 'Reminder', ANNOUNCEMENT: 'Announcement', PERSONAL: 'Personal' }

/** A plain word for the message type; an unknown type shows as it came. */
export function typeLabel(type) {
  return TYPE_LABELS[type] || type || ''
}

/** "1 attempt", "3 attempts"; empty when none were made (or the count is unknown). */
export function attemptsLabel(n) {
  return n > 0 ? `${n} ${n === 1 ? 'attempt' : 'attempts'}` : ''
}

/**
 * The delivery counts of one message as words with a tone for StatusBadge: "8 delivered" (sent plus
 * delivered, both mean the mail left), "1 failed", "1 pending". Zero counts are left out.
 * @returns {{ key: string, text: string, tone: 'paid'|'danger'|'behind' }[]}
 */
export function deliverySummaryParts(summary) {
  const { sent = 0, delivered = 0, failed = 0, pending = 0 } = summary || {}
  const parts = [
    { key: 'delivered', count: sent + delivered, word: 'delivered', tone: 'paid' },
    { key: 'failed', count: failed, word: 'failed', tone: 'danger' },
    { key: 'pending', count: pending, word: 'pending', tone: 'behind' }
  ]
  return parts.filter(part => part.count > 0).map(({ key, count, word, tone }) => ({ key, text: `${count} ${word}`, tone }))
}

/** StatusLabel tone and word for one delivery status. SENT reads Delivered: the mail left, as the totals say. */
export function deliveryStatus(status) {
  switch (status) {
    case 'SENT': return { tone: 'paid', label: 'Delivered' }
    case 'DELIVERED': return { tone: 'paid', label: 'Delivered' }
    case 'FAILED': return { tone: 'danger', label: 'Failed' }
    case 'PENDING': return { tone: 'behind', label: 'Pending' }
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

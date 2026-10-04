import { format } from 'date-fns'

const TYPES = [
  { value: 'SIGN_IN', label: 'Signed in' },
  { value: 'PASSWORD_CHANGED', label: 'Password changed' },
  { value: 'MEMBER_CREATED', label: 'Member added' },
  { value: 'MEMBER_UPDATED', label: 'Member edited' },
  { value: 'MEMBER_ACTIVATED', label: 'Member reactivated' },
  { value: 'MEMBER_DEACTIVATED', label: 'Member deactivated' },
  { value: 'MEMBER_ARCHIVED', label: 'Member archived' },
  { value: 'MEMBER_DELETED', label: 'Member deleted' },
  { value: 'MEMBER_HOUSEHOLD_CHANGED', label: 'Member household changed' },
  { value: 'MEMBERS_EXPORTED', label: 'Members exported' },
  { value: 'HOUSEHOLD_CREATED', label: 'Household added' },
  { value: 'HOUSEHOLD_UPDATED', label: 'Household edited' },
  { value: 'HOUSEHOLD_DELETED', label: 'Household deleted' },
  { value: 'PERSON_CREATED', label: 'Person added' },
  { value: 'PERSON_UPDATED', label: 'Person edited' },
  { value: 'PERSON_DELETED', label: 'Person deleted' },
  { value: 'MEMBERSHIP_STARTED', label: 'Membership started' },
  { value: 'PAYMENT_RECORDED', label: 'Payment recorded' },
  { value: 'PAYMENTS_EXPORTED', label: 'Payments exported' },
  { value: 'MESSAGE_SENT', label: 'Message sent' },
  // Written by the sample data only; they stay readable in existing databases
  { value: 'SYSTEM_STARTUP', label: 'System started' },
  { value: 'BULK_IMPORT', label: 'Members imported' },
  { value: 'PAYMENT_REMINDER_SENT', label: 'Reminders sent' }
]

/** Every activity type the server records, with a plain label, in the order the filter lists them. */
export const ACTIVITY_TYPES = TYPES

/** A plain label for an activity type; an unknown type shows as it came. */
export function activityTypeLabel(type) {
  return TYPES.find(item => item.value === type)?.label || type || ''
}

/** Who did it: the email, or "System" for the scheduled job (the server writes "system") or a missing actor. */
export function actorLabel(actor) {
  return !actor || String(actor).toLowerCase() === 'system' ? 'System' : actor
}

/** Entries of one type; "ALL" or no type keeps everything. Does not change the input. */
export function filterByType(entries, type) {
  const list = Array.isArray(entries) ? entries : []
  return !type || type === 'ALL' ? list : list.filter(entry => entry.type === type)
}

export const PAGE_SIZE = 50
export const MAX_LIMIT = 200

/** The next limit for "Show more": 50 more, never above the server maximum of 200. */
export function nextLimit(limit) {
  return Math.min(limit + PAGE_SIZE, MAX_LIMIT)
}

const TONES = {
  PAYMENT_RECORDED: 'paid',
  MESSAGE_SENT: 'paid',
  PAYMENT_REMINDER_SENT: 'paid',
  MEMBERS_EXPORTED: 'behind',
  PAYMENTS_EXPORTED: 'behind',
  MEMBER_ARCHIVED: 'danger',
  MEMBER_DELETED: 'danger',
  HOUSEHOLD_DELETED: 'danger',
  PERSON_DELETED: 'danger'
}

/** The StatusBadge tone for a type: payments and messages success, exports warning, archive and delete danger, the rest neutral. */
export function activityTone(type) {
  return TONES[type] || 'muted'
}

const dayKey = date => format(date, 'yyyy-MM-dd')

/** "Today, Sunday 4 October", "Saturday 3 October" (yesterday), then with the year for older days ("Wednesday 30 September 2026"). Local time. */
export function dayHeading(date, now = new Date()) {
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  const today = dayKey(date) === dayKey(now)
  const recent = today || dayKey(date) === dayKey(yesterday)
  const text = format(date, recent ? 'EEEE d MMMM' : 'EEEE d MMMM yyyy')
  return today ? `Today, ${text}` : text
}

/**
 * Entries grouped by local day, in the order given (newest first stays newest first):
 * [{ key, heading, entries }]. An entry without a readable createdAt is left out.
 */
export function groupByDay(entries, now = new Date()) {
  const groups = []
  for (const entry of Array.isArray(entries) ? entries : []) {
    const date = new Date(entry.createdAt)
    if (!entry.createdAt || Number.isNaN(date.getTime())) continue
    const key = dayKey(date)
    let group = groups[groups.length - 1]
    if (!group || group.key !== key) {
      group = { key, heading: dayHeading(date, now), entries: [] }
      groups.push(group)
    }
    group.entries.push(entry)
  }
  return groups
}

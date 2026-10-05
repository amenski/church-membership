import { format } from 'date-fns'
import { formatDate } from '@/utils'

// The words live in locales/ (activity.*)
const TYPES = [
  { value: 'SIGN_IN', labelKey: 'activity.signIn' },
  { value: 'PASSWORD_CHANGED', labelKey: 'activity.passwordChanged' },
  { value: 'MEMBER_CREATED', labelKey: 'activity.memberCreated' },
  { value: 'MEMBER_UPDATED', labelKey: 'activity.memberUpdated' },
  { value: 'MEMBER_ACTIVATED', labelKey: 'activity.memberActivated' },
  { value: 'MEMBER_DEACTIVATED', labelKey: 'activity.memberDeactivated' },
  { value: 'MEMBER_ARCHIVED', labelKey: 'activity.memberArchived' },
  { value: 'MEMBER_DELETED', labelKey: 'activity.memberDeleted' },
  { value: 'MEMBER_HOUSEHOLD_CHANGED', labelKey: 'activity.memberHouseholdChanged' },
  { value: 'MEMBERS_EXPORTED', labelKey: 'activity.membersExported' },
  { value: 'HOUSEHOLD_CREATED', labelKey: 'activity.householdCreated' },
  { value: 'HOUSEHOLD_UPDATED', labelKey: 'activity.householdUpdated' },
  { value: 'HOUSEHOLD_DELETED', labelKey: 'activity.householdDeleted' },
  { value: 'PERSON_CREATED', labelKey: 'activity.personCreated' },
  { value: 'PERSON_UPDATED', labelKey: 'activity.personUpdated' },
  { value: 'PERSON_DELETED', labelKey: 'activity.personDeleted' },
  { value: 'MEMBERSHIP_STARTED', labelKey: 'activity.membershipStarted' },
  { value: 'PAYMENT_RECORDED', labelKey: 'activity.paymentRecorded' },
  { value: 'PAYMENTS_EXPORTED', labelKey: 'activity.paymentsExported' },
  { value: 'MESSAGE_SENT', labelKey: 'activity.messageSent' },
  // Written by the sample data only; they stay readable in existing databases
  { value: 'SYSTEM_STARTUP', labelKey: 'activity.systemStartup' },
  { value: 'BULK_IMPORT', labelKey: 'activity.bulkImport' },
  { value: 'PAYMENT_REMINDER_SENT', labelKey: 'activity.paymentReminderSent' }
]

/** Every activity type the server records, with the key of its label, in the order the filter lists them. */
export const ACTIVITY_TYPES = TYPES

/** The i18n key of a type's label; an unknown type is shown as it came. */
export function activityTypeKey(type) {
  return TYPES.find(item => item.value === type)?.labelKey || type || ''
}

/**
 * Who did it: the email, or the System word for the scheduled job (the server writes "system") or a
 * missing actor.
 * @param {string} actor
 * @param {Function} t useI18n's t
 */
export function actorLabel(actor, t) {
  return !actor || String(actor).toLowerCase() === 'system' ? t('activity.system') : actor
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

/**
 * "Today, Sunday 4 October", "Saturday 3 October" (yesterday), then with the year for older days
 * ("Wednesday 30 September 2026"). Local time, in the UI language.
 * @param {Date} date
 * @param {Function} t useI18n's t
 * @param {Date} [now]
 */
export function dayHeading(date, t, now = new Date()) {
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  const today = dayKey(date) === dayKey(now)
  const recent = today || dayKey(date) === dayKey(yesterday)
  const text = formatDate(date, recent ? 'EEEE d MMMM' : 'EEEE d MMMM yyyy')
  return today ? t('activity.today', { date: text }) : text
}

/**
 * Entries grouped by local day, in the order given (newest first stays newest first):
 * [{ key, heading, entries }]. An entry without a readable createdAt is left out.
 * @param {object[]} entries
 * @param {Function} t useI18n's t
 * @param {Date} [now]
 */
export function groupByDay(entries, t, now = new Date()) {
  const groups = []
  for (const entry of Array.isArray(entries) ? entries : []) {
    const date = new Date(entry.createdAt)
    if (!entry.createdAt || Number.isNaN(date.getTime())) continue
    const key = dayKey(date)
    let group = groups[groups.length - 1]
    if (!group || group.key !== key) {
      group = { key, heading: dayHeading(date, t, now), entries: [] }
      groups.push(group)
    }
    group.entries.push(entry)
  }
  return groups
}

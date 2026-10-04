const TYPES = [
  { value: 'SIGN_IN', label: 'Signed in' },
  { value: 'PASSWORD_CHANGED', label: 'Password changed' },
  { value: 'MEMBER_CREATED', label: 'Member added' },
  { value: 'MEMBER_UPDATED', label: 'Member edited' },
  { value: 'MEMBER_ACTIVATED', label: 'Member reactivated' },
  { value: 'MEMBER_DEACTIVATED', label: 'Member deactivated' },
  { value: 'MEMBER_ARCHIVED', label: 'Member archived' },
  { value: 'MEMBER_DELETED', label: 'Member deleted' },
  { value: 'MEMBERS_EXPORTED', label: 'Members exported' },
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

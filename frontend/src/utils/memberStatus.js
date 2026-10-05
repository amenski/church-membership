// The five membership statuses of the backend (MemberStatus.java) and the one rule that depends on them:
// only a MEMBER pays dues, is counted as behind, gets messages and can have a payment recorded.

// The words live in locales/ (status.*); these are the keys that reach them
export const STATUS_KEYS = {
  MEMBER: 'status.member',
  INACTIVE: 'status.inactive',
  DECEASED: 'status.deceased',
  TRANSFERRED: 'status.transferred',
  ARCHIVED: 'status.archived'
}

// StatusLabel tones: a member who is paid is fern; every other status is neutral. An inactive
// member is routine, not an error, so it must not borrow the clay (danger) tone.
const TONES = {
  MEMBER: 'paid',
  INACTIVE: 'muted',
  DECEASED: 'muted',
  TRANSFERRED: 'muted',
  ARCHIVED: 'muted'
}

/** The options of the Status select when editing. Archived is not one: it is the menu action. */
export const STATUS_OPTIONS = [
  { value: 'MEMBER', labelKey: STATUS_KEYS.MEMBER },
  { value: 'INACTIVE', labelKey: STATUS_KEYS.INACTIVE },
  { value: 'DECEASED', labelKey: STATUS_KEYS.DECEASED },
  { value: 'TRANSFERRED', labelKey: STATUS_KEYS.TRANSFERRED }
]

/** A new member can only start as Member or Inactive (the server refuses the others). */
export const NEW_MEMBER_STATUS_OPTIONS = STATUS_OPTIONS.filter(option => option.value === 'MEMBER' || option.value === 'INACTIVE')

/** The one rule: dues, "behind", reminders, messages and payments apply to status MEMBER only. */
export function countsForDues(member) {
  return member?.status === 'MEMBER'
}

export function isArchived(member) {
  return member?.status === 'ARCHIVED'
}

/** The i18n key of a status name; an unknown value shows as it came. */
export function statusKey(status) {
  return STATUS_KEYS[status] || status || ''
}

export function statusTone(status) {
  return TONES[status] || 'muted'
}

// The five membership statuses of the backend (MemberStatus.java) and the one rule that depends on them:
// only a MEMBER pays dues, is counted as behind, gets messages and can have a payment recorded.

export const STATUS_LABELS = {
  MEMBER: 'Member',
  INACTIVE: 'Inactive',
  DECEASED: 'Deceased',
  TRANSFERRED: 'Transferred',
  ARCHIVED: 'Archived'
}

// StatusLabel tones: a member who is paid is fern; Inactive keeps its old clay; the rest are muted
const TONES = {
  MEMBER: 'paid',
  INACTIVE: 'inactive',
  DECEASED: 'muted',
  TRANSFERRED: 'muted',
  ARCHIVED: 'muted'
}

/** The options of the Status select when editing. Archived is not one: it is the menu action. */
export const STATUS_OPTIONS = [
  { value: 'MEMBER', label: STATUS_LABELS.MEMBER },
  { value: 'INACTIVE', label: STATUS_LABELS.INACTIVE },
  { value: 'DECEASED', label: STATUS_LABELS.DECEASED },
  { value: 'TRANSFERRED', label: STATUS_LABELS.TRANSFERRED }
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

/** The word for a status; an unknown value shows as it came. */
export function statusLabel(status) {
  return STATUS_LABELS[status] || status || ''
}

export function statusTone(status) {
  return TONES[status] || 'muted'
}

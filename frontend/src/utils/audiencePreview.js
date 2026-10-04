import { countsForDues } from '@/utils/memberStatus'
import { emailKey } from '@/utils/audienceCount'

const byName = (a, b) => a.name.localeCompare(b.name)
/** "1 person", "3 people". */
export const personLabel = n => `${n} ${n === 1 ? 'person' : 'people'}`

/**
 * Who a message to this audience would reach, with what happens to each one. Only members with status
 * MEMBER are ever in it (the server leaves out the others). A member without an email is 'noEmail' (skipped);
 * members who share an address get one message between them, the one with the lowest id ('send'), and
 * the others are 'shared' (skipped), as Recipients.reachable does on the server.
 * ALL is by name, OVERDUE is the furthest behind first, SPECIFIC is the chosen member (none yet gives []).
 * @returns {{ member: object, state: 'send'|'noEmail'|'shared', sharesWith: string }[]}
 */
export function previewRecipients(members, audience, months, memberId) {
  const active = (Array.isArray(members) ? members : []).filter(countsForDues)
  let chosen = []
  if (audience === 'ALL') {
    chosen = [...active].sort(byName)
  } else if (audience === 'OVERDUE') {
    const threshold = Number(months)
    if (threshold >= 1) {
      chosen = active
        .filter(member => (member.consecutiveMonthsMissed || 0) >= threshold)
        .sort((a, b) => (b.consecutiveMonthsMissed || 0) - (a.consecutiveMonthsMissed || 0) || byName(a, b))
    }
  } else if (audience === 'SPECIFIC') {
    chosen = active.filter(member => String(member.id) === String(memberId))
  }
  const firstWithAddress = new Map()
  for (const member of [...chosen].sort((a, b) => a.id - b.id)) {
    const key = emailKey(member)
    if (key && !firstWithAddress.has(key)) firstWithAddress.set(key, member)
  }
  return chosen.map(member => {
    const key = emailKey(member)
    if (!key) return { member, state: 'noEmail', sharesWith: '' }
    const first = firstWithAddress.get(key)
    if (first.id !== member.id) return { member, state: 'shared', sharesWith: first.name }
    return { member, state: 'send', sharesWith: '' }
  })
}

/** How many people get an email: the figure on the send button and in the confirm dialog. */
export function sendableCount(rows) {
  return rows.filter(row => row.state === 'send').length
}

function emailSentence(total, withEmail) {
  if (total === 0) return ''
  if (withEmail === total) return total === 1 ? 'They have an email.' : total === 2 ? 'Both have an email.' : 'All of them have an email.'
  if (withEmail === 0) return total === 1 ? 'They have no email.' : 'None of them has an email.'
  return `${withEmail} of them have an email.`
}

/** The count line above the list: "3 members owe 2 or more months. 2 of them have an email." */
export function previewSummary(rows, audience, months) {
  const total = rows.length
  const withEmail = rows.filter(row => row.state !== 'noEmail').length
  const members = n => `${n} ${n === 1 ? 'member' : 'members'}`
  if (audience === 'SPECIFIC') {
    if (!total) return 'Choose a member to see who gets this.'
    return withEmail ? 'This member has an email.' : 'This member has no email, so nothing can be sent.'
  }
  if (audience === 'OVERDUE') {
    if (!(Number(months) >= 1)) return 'Enter how many months behind to see who gets this.'
    if (!total) return `Nobody owes ${Number(months)} or more months.`
    return `${members(total)} ${total === 1 ? 'owes' : 'owe'} ${Number(months)} or more months. ${emailSentence(total, withEmail)}`
  }
  if (!total) return 'There are no active members.'
  return `${total} active ${total === 1 ? 'member' : 'members'}. ${emailSentence(total, withEmail)}`
}

function nameList(rows) {
  const names = rows.slice(0, 5).map(row => row.member.name)
  const more = rows.length - names.length
  return more > 0 ? `${names.join(', ')} and ${more} more` : names.join(', ')
}

/** Who is left out and why, for the confirm dialog; '' when everyone gets the email. */
export function skippedSentence(rows) {
  const noEmail = rows.filter(row => row.state === 'noEmail')
  const shared = rows.filter(row => row.state === 'shared')
  const parts = []
  if (noEmail.length) parts.push(`${personLabel(noEmail.length)} ${noEmail.length === 1 ? 'has' : 'have'} no email and will be skipped: ${nameList(noEmail)}.`)
  if (shared.length) parts.push(`${personLabel(shared.length)} ${shared.length === 1 ? 'shares' : 'share'} an address with another member and will not get a second copy: ${nameList(shared)}.`)
  return parts.join(' ')
}

/** The short note by the send button: "1 person has no email and will be skipped."; '' when nobody is skipped. */
export function skippedNote(rows) {
  const skipped = rows.filter(row => row.state !== 'send').length
  if (!skipped) return ''
  if (rows.every(row => row.state !== 'shared')) return `${personLabel(skipped)} ${skipped === 1 ? 'has' : 'have'} no email and will be skipped.`
  return `${personLabel(skipped)} will be skipped.`
}

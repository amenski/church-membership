import { countsForDues } from '@/utils/memberStatus'
import { emailKey } from '@/utils/audienceCount'

const byName = (a, b) => a.name.localeCompare(b.name)
/**
 * "1 person", "3 people".
 * @param {number} n
 * @param {Function} t useI18n's t
 */
export const personLabel = (n, t) => t('messages.personCount', n)

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

function emailSentence(total, withEmail, t) {
  if (total === 0) return ''
  if (withEmail === total) {
    if (total === 1) return t('messages.emailThem')
    return total === 2 ? t('messages.emailBoth') : t('messages.emailAll')
  }
  if (withEmail === 0) return total === 1 ? t('messages.emailThemNone') : t('messages.emailNone')
  return t('messages.emailSome', { n: withEmail })
}

/**
 * The count line above the list: "3 members owe 2 or more months. 2 of them have an email."
 * @param {object[]} rows previewRecipients' output
 * @param {string} audience ALL | OVERDUE | SPECIFIC
 * @param {string|number} months
 * @param {Function} t useI18n's t
 */
export function previewSummary(rows, audience, months, t) {
  const total = rows.length
  const withEmail = rows.filter(row => row.state !== 'noEmail').length
  if (audience === 'SPECIFIC') {
    if (!total) return t('messages.chooseMember')
    return withEmail ? t('messages.thisMemberHas') : t('messages.thisMemberNone')
  }
  if (audience === 'OVERDUE') {
    if (!(Number(months) >= 1)) return t('messages.enterMonths')
    if (!total) return t('messages.nobodyOwes', { n: Number(months) })
    return `${t('messages.owesOrMore', { members: t('members.countMember', total), n: Number(months) })} ${emailSentence(total, withEmail, t)}`
  }
  if (!total) return t('messages.noActiveMembers')
  return `${t('messages.activeMembers', total)}. ${emailSentence(total, withEmail, t)}`
}

function nameList(rows, t) {
  const names = rows.slice(0, 5).map(row => row.member.name)
  const more = rows.length - names.length
  return more > 0
    ? t('messages.namesAndMore', { names: names.join(t('common.listSeparator')), more })
    : names.join(t('common.listSeparator'))
}

/**
 * Who is left out and why, for the confirm dialog; '' when everyone gets the email.
 * @param {Function} t useI18n's t
 */
export function skippedSentence(rows, t) {
  const noEmail = rows.filter(row => row.state === 'noEmail')
  const shared = rows.filter(row => row.state === 'shared')
  const parts = []
  if (noEmail.length) {
    parts.push(t('messages.skippedNoEmailSentence', { people: personLabel(noEmail.length, t), names: nameList(noEmail, t) }))
  }
  if (shared.length) {
    parts.push(t('messages.skippedSharedSentence', { people: personLabel(shared.length, t), names: nameList(shared, t) }))
  }
  return parts.join(' ')
}

/** The short note by the send button: "1 person with no email will be skipped."; '' when nobody is skipped. */
export function skippedNote(rows, t) {
  const skipped = rows.filter(row => row.state !== 'send').length
  if (!skipped) return ''
  if (rows.every(row => row.state !== 'shared')) {
    return t('messages.skippedNoteNoEmail', { people: personLabel(skipped, t) })
  }
  return t('messages.skippedNoteSome', { people: personLabel(skipped, t) })
}

import { countsForDues } from '@/utils/memberStatus'

// Messages go by email address: members without one are skipped and members who share an
// address get one message between them (case and spaces ignored), as the server does.
/** The address a message goes to, as the server compares it: trimmed, lower case; '' when there is none. */
export function emailKey(member) {
  return (member.email || '').trim().toLowerCase()
}

function distinctAddresses(members) {
  const seen = new Set()
  for (const member of members) {
    const address = emailKey(member)
    if (address) seen.add(address)
  }
  return seen.size
}

/**
 * How many messages go out, as the server will count them. Only members with status MEMBER get messages:
 * ALL is every such member, OVERDUE is those who are at least `months` months
 * behind, SPECIFIC is the one chosen member. ALL and OVERDUE count distinct non-empty addresses.
 */
export function audienceCount(members, audience, months) {
  const active = (Array.isArray(members) ? members : []).filter(countsForDues)
  if (audience === 'ALL') return distinctAddresses(active)
  if (audience === 'OVERDUE') {
    const threshold = Number(months)
    if (!(threshold >= 1)) return 0
    return distinctAddresses(active.filter(member => member.consecutiveMonthsMissed >= threshold))
  }
  if (audience === 'SPECIFIC') return 1
  return 0
}

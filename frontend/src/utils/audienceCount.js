// Messages go by email address: members without one are skipped and members who share an
// address get one message between them (case and spaces ignored), as the server does.
function distinctAddresses(members) {
  const seen = new Set()
  for (const member of members) {
    const address = (member.email || '').trim().toLowerCase()
    if (address) seen.add(address)
  }
  return seen.size
}

/**
 * How many messages go out, as the server will count them. Only ACTIVE members get messages:
 * ALL is every active member, OVERDUE is active members who are at least `months` months
 * behind, SPECIFIC is the one chosen member. ALL and OVERDUE count distinct non-empty addresses.
 */
export function audienceCount(members, audience, months) {
  const active = (Array.isArray(members) ? members : []).filter(member => member.active)
  if (audience === 'ALL') return distinctAddresses(active)
  if (audience === 'OVERDUE') {
    const threshold = Number(months)
    if (!(threshold >= 1)) return 0
    return distinctAddresses(active.filter(member => member.consecutiveMonthsMissed >= threshold))
  }
  if (audience === 'SPECIFIC') return 1
  return 0
}

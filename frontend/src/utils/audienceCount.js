/**
 * How many members a message goes to, as the server will count them. Only ACTIVE members get
 * messages: ALL is every active member, OVERDUE is active members who are at least `months`
 * months behind, SPECIFIC is the one chosen member.
 */
export function audienceCount(members, audience, months) {
  const active = (Array.isArray(members) ? members : []).filter(member => member.active)
  if (audience === 'ALL') return active.length
  if (audience === 'OVERDUE') {
    const threshold = Number(months)
    if (!(threshold >= 1)) return 0
    return active.filter(member => member.consecutiveMonthsMissed >= threshold).length
  }
  if (audience === 'SPECIFIC') return 1
  return 0
}

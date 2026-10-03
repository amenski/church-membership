const digitsOf = (value) => (value || '').replace(/\D/g, '')
const dayOf = (value) => (value ? String(value).slice(0, 10) : '')

export function filterMembers(members, filters = {}) {
  const { status = 'ALL', paymentStatus = 'ALL', joinedFrom = '', joinedTo = '' } = filters
  const term = (filters.search || '').trim().toLowerCase()
  const termDigits = digitsOf(term)

  return members.filter(member => {
    const matchesSearch = !term ||
      (member.name || '').toLowerCase().includes(term) ||
      (member.email || '').toLowerCase().includes(term) ||
      (member.phone || '').toLowerCase().includes(term) ||
      (termDigits.length >= 3 && digitsOf(member.phone).includes(termDigits))

    const matchesStatus = status === 'ALL' ||
      (status === 'ACTIVE' && member.active) ||
      (status === 'INACTIVE' && !member.active)

    // Behind and paid up only mean something for ACTIVE members: the server stops counting
    // months for an inactive one, so its stored number is stale. It matches "All" only.
    const matchesPayment = paymentStatus === 'ALL' ||
      (member.active &&
        ((paymentStatus === 'OVERDUE' && member.consecutiveMonthsMissed > 0) ||
         (paymentStatus === 'CURRENT' && member.consecutiveMonthsMissed === 0)))

    let matchesJoinDate = true
    if (joinedFrom || joinedTo) {
      const joined = dayOf(member.joinDate)
      matchesJoinDate = !!joined &&
        (!joinedFrom || joined >= joinedFrom) &&
        (!joinedTo || joined <= joinedTo)
    }

    return matchesSearch && matchesStatus && matchesPayment && matchesJoinDate
  })
}

export function sortMembers(members, key, direction = 'asc') {
  const sign = direction === 'desc' ? -1 : 1
  const valueOf = (member) => {
    // an inactive member has no dues figure, so it sorts last either way
    if (key === 'consecutiveMonthsMissed' && member.active === false) return null
    const value = member[key]
    if (value === null || value === undefined || value === '') return null
    return key === 'name' ? String(value) : value
  }

  return [...members].sort((a, b) => {
    const va = valueOf(a)
    const vb = valueOf(b)
    if (va === null && vb === null) return 0
    if (va === null) return 1
    if (vb === null) return -1
    const result = key === 'name'
      ? va.localeCompare(vb, undefined, { sensitivity: 'base' })
      : (va < vb ? -1 : va > vb ? 1 : 0)
    return result * sign
  })
}

// Ids to send to the export endpoint: none (= export everything) when the
// filter leaves the whole list on screen, otherwise the ids that are visible.
export function exportIds(filtered, all) {
  if (filtered.length === all.length) return []
  return filtered.map(member => member.id)
}

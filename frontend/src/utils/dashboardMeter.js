// Pure helpers for the overview page's dues meter.

/**
 * One segment per active member, capped at maxSegments.
 * `filled` is the number of segments that show as paid up.
 */
export function meterSegments(total, paid, maxSegments = 40) {
  const members = Number(total)
  if (!Number.isFinite(members) || members <= 0) return { segments: 0, filled: 0 }
  const segments = Math.min(members, maxSegments)
  const paidUp = Math.min(Math.max(Number(paid) || 0, 0), members)
  return { segments, filled: Math.round((paidUp / members) * segments) }
}

/** "1 month behind", "2 months behind". */
export function monthsBehind(n) {
  return `${n} ${n === 1 ? 'month' : 'months'} behind`
}

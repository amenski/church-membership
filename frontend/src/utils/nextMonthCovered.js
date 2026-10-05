// Month covered on Record payment follows the chosen member: their oldest unpaid month, or the
// current month when there is none. A month the user typed (anything but the value last written
// here) is never replaced.

/**
 * @param {string} lastAuto the month this rule last wrote ('' if none)
 * @param {string} current the month now in the field
 * @param {string} oldestUnpaid the chosen member's oldest unpaid month ('' if none or no member)
 * @param {string} currentMonth this month, "YYYY-MM"
 * @returns {{ period: string, auto: string }} the field's new value and the value to remember
 */
export function nextMonthCovered(lastAuto, current, oldestUnpaid, currentMonth) {
  if (current !== lastAuto) return { period: current, auto: lastAuto }
  const period = oldestUnpaid || currentMonth
  return { period, auto: period }
}

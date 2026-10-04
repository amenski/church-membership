// Wording for the dues figures. `Recipients` and the overdue queries own the rules; this is only how
// the overdue count reads on the Overview and in the member list.

/** "1 month behind", "2 months behind". */
export function monthsBehind(n) {
  return `${n} ${n === 1 ? 'month' : 'months'} behind`
}

// Which row is highlighted in a listbox after a key. -1 means none: the list opens with nothing
// highlighted, Down then goes to the first row and Up to the last, both wrapping afterwards.

/**
 * @param {number} current the highlighted row, -1 for none
 * @param {string} key 'ArrowDown', 'ArrowUp', 'Home' or 'End'
 * @param {number} count rows in the list
 * @returns {number} the row to highlight, -1 when the list is empty
 */
export function nextIndex(current, key, count) {
  if (count <= 0) return -1
  switch (key) {
    case 'ArrowDown':
      return current < 0 ? 0 : (current + 1) % count
    case 'ArrowUp':
      return current < 0 ? count - 1 : (current - 1 + count) % count
    case 'Home':
      return 0
    case 'End':
      return count - 1
    default:
      return current
  }
}

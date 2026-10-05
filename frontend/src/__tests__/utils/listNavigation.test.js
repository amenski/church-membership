import { describe, it, expect } from 'vitest'
import { nextIndex } from '@/utils/listNavigation'

describe('nextIndex', () => {
  it('Down from no highlight goes to the first row, Up to the last', () => {
    expect(nextIndex(-1, 'ArrowDown', 5)).toBe(0)
    expect(nextIndex(-1, 'ArrowUp', 5)).toBe(4)
  })

  it('moves one row and wraps at both ends', () => {
    expect(nextIndex(0, 'ArrowDown', 5)).toBe(1)
    expect(nextIndex(4, 'ArrowDown', 5)).toBe(0)
    expect(nextIndex(2, 'ArrowUp', 5)).toBe(1)
    expect(nextIndex(0, 'ArrowUp', 5)).toBe(4)
  })

  it('Home and End jump to the first and last row, highlighted or not', () => {
    expect(nextIndex(-1, 'Home', 5)).toBe(0)
    expect(nextIndex(2, 'End', 5)).toBe(4)
  })

  it('has no highlight in an empty list and ignores other keys', () => {
    expect(nextIndex(-1, 'ArrowDown', 0)).toBe(-1)
    expect(nextIndex(0, 'End', 0)).toBe(-1)
    expect(nextIndex(2, 'a', 5)).toBe(2)
  })
})

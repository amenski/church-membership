import { describe, it, expect } from 'vitest'
import { monthsBehind } from '@/utils/dues'

describe('monthsBehind', () => {
  it('uses the singular for one month', () => {
    expect(monthsBehind(1)).toBe('1 month behind')
  })

  it('uses the plural otherwise', () => {
    expect(monthsBehind(2)).toBe('2 months behind')
    expect(monthsBehind(6)).toBe('6 months behind')
  })
})

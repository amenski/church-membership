import { describe, it, expect } from 'vitest'
import i18n from '@/i18n'
import { monthsBehind } from '@/utils/dues'

// src/__tests__/setup.js pins the app locale to English
const t = (...args) => i18n.global.t(...args)

describe('monthsBehind', () => {
  it('uses the singular for one month', () => {
    expect(monthsBehind(1, t)).toBe('1 month behind')
  })

  it('uses the plural otherwise', () => {
    expect(monthsBehind(2, t)).toBe('2 months behind')
    expect(monthsBehind(6, t)).toBe('6 months behind')
  })
})

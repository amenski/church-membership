import { describe, it, expect } from 'vitest'
import { isValidPhone } from '@/utils/phoneRules'

describe('isValidPhone', () => {
  it.each(['0612345678', '+39 333 1234567', '(06) 123-4567', '', '   ', null, undefined])(
    'accepts %j',
    (phone) => {
      expect(isValidPhone(phone)).toBe(true)
    }
  )

  it.each(['5551234', 'abcdefghij', '06123abc45', '+39 333', '123456789'])(
    'rejects %j',
    (phone) => {
      expect(isValidPhone(phone)).toBe(false)
    }
  )
})

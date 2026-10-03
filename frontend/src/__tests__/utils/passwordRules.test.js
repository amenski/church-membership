import { describe, it, expect } from 'vitest'
import { validateNewPassword, PASSWORD_RULE_MESSAGE } from '@/utils/passwordRules'

// Same table as the server's UserTest
describe('validateNewPassword', () => {
  it.each([
    'Abcdef1!',
    'Abcdef1-',
    'Abcdef1_x',
    'Abcdef1#',
    'Abcdef1.',
    'Abcdef 1!',
    'Correct Horse 9 Battery-Staple',
    'Aa1!' + 'x'.repeat(68), // 72 bytes
    'Aa1!' + 'é'.repeat(34) // 72 bytes, 38 characters
  ])('accepts %s', (pw) => {
    expect(validateNewPassword(pw)).toBe('')
  })

  it.each([
    ['empty', ''],
    ['no special', 'Abcdefg1'],
    ['no digit', 'Abcdefg!'],
    ['no uppercase', 'abcdef1!'],
    ['no lowercase', 'ABCDEF1!'],
    ['7 characters', 'Abcde1!'],
    ['73 bytes', 'Aa1!' + 'x'.repeat(69)],
    ['74 bytes, 39 characters', 'Aa1!' + 'é'.repeat(35)],
    ['whitespace is not a special character', 'Abcdef 1'],
    ['whitespace only as the special', '   Abcd1   '],
    ['not a string', null]
  ])('rejects: %s', (_label, pw) => {
    expect(validateNewPassword(pw)).toBe(PASSWORD_RULE_MESSAGE)
  })
})

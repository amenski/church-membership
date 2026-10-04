import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildMemberRequest } from '@/utils/memberPayload'

// Shared with the backend MemberContractTest.
// vitest runs from frontend/ (jsdom makes import.meta.url a non-file URL)
const fixturePath = resolve(process.cwd(), '../src/test/resources/contracts/member-request.json')
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))

describe('buildMemberRequest', () => {
  it('matches the shared contract fixture and never sends extra properties', () => {
    const form = {
      name: ' Test Member ',
      email: 'member@example.com ',
      phone: '+39 333 1234567',
      joinDate: '2025-01-15',
      active: true,
      id: 5,
      consecutiveMonthsMissed: 3,
      lastPaymentDate: '2020-01-01',
      address: 'x'
    }
    expect(buildMemberRequest(form)).toEqual(fixture)
  })

  it('omits a blank phone', () => {
    const r = buildMemberRequest({ name: 'A', email: 'a@example.com', phone: '   ', active: true })
    expect(r).not.toHaveProperty('phone')
  })

  it('omits a blank or missing email', () => {
    expect(buildMemberRequest({ name: 'A', email: '  ', active: true })).not.toHaveProperty('email')
    expect(buildMemberRequest({ name: 'A', active: true })).not.toHaveProperty('email')
  })

  it('omits joinDate when not set', () => {
    const r = buildMemberRequest({ name: 'A', email: 'a@example.com', joinDate: null, active: true })
    expect(r).not.toHaveProperty('joinDate')
  })

  it('coerces active to a boolean', () => {
    expect(buildMemberRequest({ name: 'A', email: 'a@example.com' }).active).toBe(false)
  })
})

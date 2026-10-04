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
      status: 'MEMBER',
      active: false,
      id: 5,
      consecutiveMonthsMissed: 3,
      lastPaymentDate: '2020-01-01',
      address: 'x'
    }
    expect(buildMemberRequest(form)).toEqual(fixture)
  })

  it('omits a blank phone', () => {
    const r = buildMemberRequest({ name: 'A', email: 'a@example.com', phone: '   ', status: 'MEMBER' })
    expect(r).not.toHaveProperty('phone')
  })

  it('omits a blank or missing email', () => {
    expect(buildMemberRequest({ name: 'A', email: '  ', status: 'MEMBER' })).not.toHaveProperty('email')
    expect(buildMemberRequest({ name: 'A', status: 'MEMBER' })).not.toHaveProperty('email')
  })

  it('omits joinDate when not set', () => {
    const r = buildMemberRequest({ name: 'A', email: 'a@example.com', joinDate: null, status: 'MEMBER' })
    expect(r).not.toHaveProperty('joinDate')
  })

  it('sends the status and never the legacy active flag', () => {
    const r = buildMemberRequest({ name: 'A', email: 'a@example.com', status: 'TRANSFERRED', active: true })
    expect(r.status).toBe('TRANSFERRED')
    expect(r).not.toHaveProperty('active')
  })

  it('defaults to MEMBER when the form has no status', () => {
    expect(buildMemberRequest({ name: 'A', email: 'a@example.com' }).status).toBe('MEMBER')
  })
})

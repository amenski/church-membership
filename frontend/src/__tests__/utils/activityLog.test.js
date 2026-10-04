import { describe, it, expect } from 'vitest'
import { ACTIVITY_TYPES, MAX_LIMIT, PAGE_SIZE, actorLabel, activityTypeLabel, filterByType, nextLimit } from '@/utils/activityLog'

// Every ActivityType of the backend (domain/enumeration/ActivityType.java)
const SERVER_TYPES = [
  'SIGN_IN', 'PASSWORD_CHANGED', 'MEMBER_CREATED', 'MEMBER_UPDATED', 'MEMBER_ACTIVATED', 'MEMBER_DEACTIVATED',
  'MEMBER_DELETED', 'MEMBERS_EXPORTED', 'PAYMENT_RECORDED', 'PAYMENTS_EXPORTED', 'MESSAGE_SENT',
  'SYSTEM_STARTUP', 'BULK_IMPORT', 'PAYMENT_REMINDER_SENT'
]

describe('activityTypeLabel', () => {
  it('has a plain label for every type the server records', () => {
    for (const type of SERVER_TYPES) {
      const label = activityTypeLabel(type)
      expect(label).not.toBe(type)
      expect(label).toMatch(/^[A-Z][a-z]+( [a-z]+)*$/)
    }
    expect(activityTypeLabel('MEMBER_CREATED')).toBe('Member added')
    expect(activityTypeLabel('MESSAGE_SENT')).toBe('Message sent')
  })

  it('lists exactly the server types, once each, for the filter', () => {
    expect(ACTIVITY_TYPES.map(item => item.value)).toEqual(SERVER_TYPES)
  })

  it('shows an unknown type as it came and nothing for none', () => {
    expect(activityTypeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(activityTypeLabel(null)).toBe('')
    expect(activityTypeLabel(undefined)).toBe('')
  })
})

describe('actorLabel', () => {
  it('shows the email', () => {
    expect(actorLabel('staff@membertracker.com')).toBe('staff@membertracker.com')
  })

  it('falls back to System for the scheduler and for a missing actor', () => {
    expect(actorLabel('system')).toBe('System')
    expect(actorLabel('SYSTEM')).toBe('System')
    expect(actorLabel(null)).toBe('System')
    expect(actorLabel('')).toBe('System')
  })
})

describe('filterByType', () => {
  const entries = [{ id: 3, type: 'MEMBER_CREATED' }, { id: 2, type: 'SIGN_IN' }, { id: 1, type: 'MEMBER_CREATED' }]

  it('keeps everything for All activity', () => {
    expect(filterByType(entries, 'ALL')).toEqual(entries)
    expect(filterByType(entries, '')).toEqual(entries)
  })

  it('keeps one type, in order, without changing the input', () => {
    expect(filterByType(entries, 'MEMBER_CREATED').map(e => e.id)).toEqual([3, 1])
    expect(entries).toHaveLength(3)
  })

  it('is empty for no list', () => {
    expect(filterByType(undefined, 'SIGN_IN')).toEqual([])
  })
})

describe('nextLimit', () => {
  it('raises the limit by 50 up to the server maximum of 200', () => {
    expect(PAGE_SIZE).toBe(50)
    expect(MAX_LIMIT).toBe(200)
    expect(nextLimit(50)).toBe(100)
    expect(nextLimit(150)).toBe(200)
    expect(nextLimit(200)).toBe(200)
  })
})

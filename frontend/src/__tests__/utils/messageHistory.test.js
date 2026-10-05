import { describe, it, expect } from 'vitest'
import i18n from '@/i18n'
import { attemptsLabel, friendlyNotes, countDeliveries, deliveryStatus, deliverySummaryParts, sortMessages, typeKey } from '@/utils/messageHistory'

// src/__tests__/setup.js pins the app locale to English
const t = (...args) => i18n.global.t(...args)

describe('deliverySummaryParts', () => {
  it('joins sent and delivered and tones each word', () => {
    expect(deliverySummaryParts({ sent: 0, failed: 1, pending: 1, delivered: 8 }, t)).toEqual([
      { key: 'delivered', count: 8, text: '8 delivered', tone: 'paid' },
      { key: 'failed', count: 1, text: '1 failed', tone: 'danger' },
      { key: 'pending', count: 1, text: '1 pending', tone: 'behind' }
    ])
  })

  it('leaves zero counts out', () => {
    expect(deliverySummaryParts({ sent: 2, failed: 0, pending: 0, delivered: 1 }, t).map(p => p.text)).toEqual(['3 delivered'])
  })

  it('is empty for no summary or all zeros', () => {
    expect(deliverySummaryParts(undefined, t)).toEqual([])
    expect(deliverySummaryParts({ sent: 0, failed: 0, pending: 0, delivered: 0 }, t)).toEqual([])
  })
})

describe('attemptsLabel', () => {
  it('counts attempts in words', () => {
    expect(attemptsLabel(1, t)).toBe('1 attempt')
    expect(attemptsLabel(3, t)).toBe('3 attempts')
  })

  it('is empty for zero, missing or invalid counts', () => {
    expect(attemptsLabel(0, t)).toBe('')
    expect(attemptsLabel(undefined, t)).toBe('')
    expect(attemptsLabel(null, t)).toBe('')
    expect(attemptsLabel(-1, t)).toBe('')
  })
})

describe('typeKey', () => {
  it('maps each type to its i18n key', () => {
    expect(t(typeKey('REMINDER'))).toBe('Reminder')
    expect(t(typeKey('ANNOUNCEMENT'))).toBe('Announcement')
    expect(t(typeKey('PERSONAL'))).toBe('Personal')
    expect(typeKey('X')).toBe('X')
    expect(typeKey(null)).toBe('')
  })
})

describe('deliveryStatus and countDeliveries', () => {
  it('maps each status to a tone and a word', () => {
    expect(deliveryStatus('SENT', t)).toEqual({ tone: 'paid', label: 'Delivered' })
    expect(deliveryStatus('DELIVERED', t)).toEqual({ tone: 'paid', label: 'Delivered' })
    expect(deliveryStatus('FAILED', t)).toEqual({ tone: 'danger', label: 'Failed' })
    expect(deliveryStatus('PENDING', t)).toEqual({ tone: 'behind', label: 'Pending' })
  })

  it('counts a delivery list', () => {
    const list = [{ status: 'SENT' }, { status: 'FAILED' }, { status: 'FAILED' }, { status: 'DELIVERED' }]
    expect(countDeliveries(list)).toEqual({ sent: 1, delivered: 1, failed: 2, pending: 0 })
  })
})

describe('sortMessages', () => {
  it('puts the newest first and does not change the input', () => {
    const list = [
      { id: 1, sentDate: '2026-01-01T10:00:00' },
      { id: 2, sentDate: '2026-03-01T10:00:00' },
      { id: 3, createdDate: '2026-02-01T10:00:00' }
    ]
    expect(sortMessages(list).map(m => m.id)).toEqual([2, 3, 1])
    expect(list.map(m => m.id)).toEqual([1, 2, 3])
  })
})

describe('friendlyNotes', () => {
  it('turns an ISO timestamp inside the note into a readable time', () => {
    expect(friendlyNotes('Retry failed at 2026-10-04T05:29:05.240703')).toBe('Retry failed at Oct 4, 2026, 5:29 AM')
  })

  it('leaves text without a timestamp alone', () => {
    expect(friendlyNotes('Mailbox full')).toBe('Mailbox full')
    expect(friendlyNotes(null)).toBe('')
  })
})

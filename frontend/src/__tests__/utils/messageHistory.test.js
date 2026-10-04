import { describe, it, expect } from 'vitest'
import { attemptsLabel, friendlyNotes, countDeliveries, deliveryStatus, deliverySummaryParts, sortMessages, typeLabel } from '@/utils/messageHistory'

describe('deliverySummaryParts', () => {
  it('joins sent and delivered and tones each word', () => {
    expect(deliverySummaryParts({ sent: 0, failed: 1, pending: 1, delivered: 8 })).toEqual([
      { key: 'sent', text: '8 sent', tone: 'paid' },
      { key: 'failed', text: '1 failed', tone: 'inactive' },
      { key: 'pending', text: '1 pending', tone: 'behind' }
    ])
  })

  it('leaves zero counts out', () => {
    expect(deliverySummaryParts({ sent: 2, failed: 0, pending: 0, delivered: 1 }).map(p => p.text)).toEqual(['3 sent'])
  })

  it('is empty for no summary or all zeros', () => {
    expect(deliverySummaryParts(undefined)).toEqual([])
    expect(deliverySummaryParts({ sent: 0, failed: 0, pending: 0, delivered: 0 })).toEqual([])
  })
})

describe('attemptsLabel', () => {
  it('counts attempts in words', () => {
    expect(attemptsLabel(1)).toBe('1 attempt')
    expect(attemptsLabel(3)).toBe('3 attempts')
  })

  it('is empty for zero, missing or invalid counts', () => {
    expect(attemptsLabel(0)).toBe('')
    expect(attemptsLabel(undefined)).toBe('')
    expect(attemptsLabel(null)).toBe('')
    expect(attemptsLabel(-1)).toBe('')
  })
})

describe('typeLabel', () => {
  it('uses a plain word', () => {
    expect(typeLabel('REMINDER')).toBe('Reminder')
    expect(typeLabel('ANNOUNCEMENT')).toBe('Announcement')
    expect(typeLabel('PERSONAL')).toBe('Personal')
    expect(typeLabel('X')).toBe('X')
    expect(typeLabel(null)).toBe('')
  })
})

describe('deliveryStatus and countDeliveries', () => {
  it('maps each status to a tone and a word', () => {
    expect(deliveryStatus('SENT')).toEqual({ tone: 'paid', label: 'Sent' })
    expect(deliveryStatus('DELIVERED')).toEqual({ tone: 'paid', label: 'Delivered' })
    expect(deliveryStatus('FAILED')).toEqual({ tone: 'inactive', label: 'Failed' })
    expect(deliveryStatus('PENDING')).toEqual({ tone: 'behind', label: 'Pending' })
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

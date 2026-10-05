import { describe, it, expect } from 'vitest'
import {
  NEW_MEMBER_STATUS_OPTIONS, STATUS_KEYS, STATUS_OPTIONS, countsForDues, isArchived, statusKey, statusTone
} from '@/utils/memberStatus'

const ALL = ['MEMBER', 'INACTIVE', 'DECEASED', 'TRANSFERRED', 'ARCHIVED']

describe('countsForDues', () => {
  it('is true only for status MEMBER', () => {
    for (const status of ALL) expect(countsForDues({ status })).toBe(status === 'MEMBER')
  })
  it('is false for a missing member, a missing status and the legacy flag alone', () => {
    expect(countsForDues(null)).toBe(false)
    expect(countsForDues(undefined)).toBe(false)
    expect(countsForDues({})).toBe(false)
    expect(countsForDues({ active: true })).toBe(false)
  })
})

describe('labels and tones', () => {
  it('maps the five statuses to their i18n keys', () => {
    expect(ALL.map(statusKey)).toEqual([
      'status.member', 'status.inactive', 'status.deceased', 'status.transferred', 'status.archived'
    ])
    expect(Object.keys(STATUS_KEYS)).toEqual(ALL)
  })
  it('shows an unknown status as it came and nothing for none', () => {
    expect(statusKey('SOMETHING')).toBe('SOMETHING')
    expect(statusKey(undefined)).toBe('')
  })
  it('uses the paid tone for Member and a neutral tone for every other status', () => {
    expect(statusTone('MEMBER')).toBe('paid')
    expect(['INACTIVE', 'DECEASED', 'TRANSFERRED', 'ARCHIVED', 'UNKNOWN'].map(statusTone))
      .toEqual(['muted', 'muted', 'muted', 'muted', 'muted'])
  })
})

describe('options', () => {
  it('offers four statuses when editing and never Archived (that is the menu action)', () => {
    expect(STATUS_OPTIONS.map(o => o.value)).toEqual(['MEMBER', 'INACTIVE', 'DECEASED', 'TRANSFERRED'])
    expect(STATUS_OPTIONS.map(o => o.labelKey)).toEqual([
      'status.member', 'status.inactive', 'status.deceased', 'status.transferred'
    ])
  })
  it('offers only Member and Inactive for a new member, as the server enforces', () => {
    expect(NEW_MEMBER_STATUS_OPTIONS.map(o => o.value)).toEqual(['MEMBER', 'INACTIVE'])
  })
  it('knows an archived member', () => {
    expect(isArchived({ status: 'ARCHIVED' })).toBe(true)
    expect(isArchived({ status: 'MEMBER' })).toBe(false)
    expect(isArchived(null)).toBe(false)
  })
})

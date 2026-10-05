import { describe, expect, it } from 'vitest'
import { format } from 'date-fns'
import { enUS } from 'date-fns/locale'
import i18n from '@/i18n'
import { am } from '@/utils/dateLocale'
import { formatDate } from '@/utils'

// A Monday, 5 October 2026
const date = new Date(2026, 9, 5)

describe('the Amharic date locale', () => {
  it('names the months in Amharic', () => {
    expect(format(date, 'MMMM', { locale: am })).toBe('ኦክቶበር')
    expect(format(date, 'MMM', { locale: am })).toBe('ኦክቶ')
  })

  it('names the weekdays in Amharic', () => {
    expect(format(date, 'EEEE', { locale: am })).toBe('ሰኞ')
    expect(format(date, 'EEE', { locale: am })).toBe('ሰኞ')
  })

  it('keeps Latin digits and the Gregorian year', () => {
    expect(format(date, 'd MMMM yyyy', { locale: am })).toBe('5 ኦክቶበር 2026')
  })

  it('leaves the English names alone', () => {
    expect(format(date, 'MMMM', { locale: enUS })).toBe('October')
  })
})

describe('formatDate', () => {
  it('follows the UI language', () => {
    i18n.global.locale.value = 'am'
    expect(formatDate('2026-10-05')).toBe('ኦክቶ 05, 2026')

    i18n.global.locale.value = 'en'
    expect(formatDate('2026-10-05')).toBe('Oct 05, 2026')
  })
})

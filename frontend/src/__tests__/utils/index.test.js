import { describe, it, expect, vi, afterEach } from 'vitest'
import { formatDate, localISODate, debounce, formatCurrency, isValidEmail, deepClone, downloadBlob } from '@/utils/index'

describe('formatDate', () => {
  it('uses the default format', () => {
    expect(formatDate(new Date(2024, 0, 5))).toBe('Jan 05, 2024')
  })
  it('accepts a custom format', () => {
    expect(formatDate(new Date(2024, 11, 25), 'yyyy-MM-dd')).toBe('2024-12-25')
  })
  it('accepts a date string', () => {
    expect(formatDate('2024-03-09T12:00:00', 'dd/MM/yyyy')).toBe('09/03/2024')
  })
  it.each([null, undefined, ''])('returns empty string for %p', (v) => {
    expect(formatDate(v)).toBe('')
  })

  // A date-only string must render as that calendar day in every time zone. The zone is the
  // test machine's, so assert against local components: UTC parsing would show 30 September
  // wherever the offset is negative.
  describe('date-only strings', () => {
    it('renders 2026-10-01 as 1 October', () => {
      expect(formatDate('2026-10-01', 'd MMMM yyyy')).toBe('1 October 2026')
    })
    it('builds a local date, so the local components match the input', () => {
      const parsed = new Date(2026, 9, 1)
      expect(formatDate('2026-10-01', 'yyyy-MM-dd HH:mm')).toBe('2026-10-01 00:00')
      expect(formatDate('2026-10-01', 'yyyy-MM-dd')).toBe(
        `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}`
      )
    })
    it('handles month and year edges', () => {
      expect(formatDate('2026-01-01', 'yyyy-MM-dd')).toBe('2026-01-01')
      expect(formatDate('2025-12-31', 'yyyy-MM-dd')).toBe('2025-12-31')
      expect(formatDate('2024-02-29', 'dd/MM/yyyy')).toBe('29/02/2024')
    })
    it('still parses a timestamp as an instant', () => {
      const instant = '2026-10-01T12:30:00Z'
      expect(formatDate(instant, 'yyyy-MM-dd HH:mm')).toBe(formatDate(new Date(instant), 'yyyy-MM-dd HH:mm'))
    })
  })
})

describe('localISODate', () => {
  it('uses the local calendar day, zero padded', () => {
    expect(localISODate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05')
    expect(localISODate(new Date(2026, 11, 31, 0, 1))).toBe('2026-12-31')
  })
})

describe('debounce', () => {
  afterEach(() => vi.useRealTimers())

  it('calls once after the wait with the last arguments', () => {
    vi.useFakeTimers()
    const fn = vi.fn()
    const d = debounce(fn, 100)
    d(1); d(2); d(3)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(99)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(fn).toHaveBeenCalledTimes(1)
    expect(fn).toHaveBeenCalledWith(3)
  })

  it('restarts the timer on each call', () => {
    vi.useFakeTimers()
    const fn = vi.fn()
    const d = debounce(fn, 100)
    d('a')
    vi.advanceTimersByTime(80)
    d('b')
    vi.advanceTimersByTime(80)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(20)
    expect(fn).toHaveBeenCalledWith('b')
  })

  it('defaults to 300ms', () => {
    vi.useFakeTimers()
    const fn = vi.fn()
    debounce(fn)()
    vi.advanceTimersByTime(299)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(fn).toHaveBeenCalledTimes(1)
  })
})

describe('formatCurrency', () => {
  it('formats USD by default', () => {
    expect(formatCurrency(1234.5)).toBe('$1,234.50')
  })
  it('formats zero', () => {
    expect(formatCurrency(0)).toBe('$0.00')
  })
  it('supports another currency', () => {
    expect(formatCurrency(10, 'EUR')).toBe('€10.00')
  })
})

describe('isValidEmail', () => {
  it.each(['a@b.co', 'first.last@example.org', 'x+tag@sub.domain.com'])('accepts %s', (e) => {
    expect(isValidEmail(e)).toBe(true)
  })
  it.each(['', 'plain', 'a@b', '@b.com', 'a@.com', 'a b@c.com', 'a@b .com'])('rejects %p', (e) => {
    expect(isValidEmail(e)).toBe(false)
  })
})

describe('deepClone', () => {
  it('produces an independent copy', () => {
    const src = { a: 1, nested: { b: [1, 2] } }
    const copy = deepClone(src)
    expect(copy).toEqual(src)
    copy.nested.b.push(3)
    expect(src.nested.b).toEqual([1, 2])
  })
})

describe('downloadBlob', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('creates an anchor with the filename, clicks it and revokes the URL', () => {
    window.URL.createObjectURL = vi.fn(() => 'blob:fake')
    window.URL.revokeObjectURL = vi.fn()
    let anchor
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function () {
        anchor = this
      })

    downloadBlob('a,b', 'test.csv')

    expect(click).toHaveBeenCalledTimes(1)
    expect(anchor.download).toBe('test.csv')
    expect(anchor.href).toBe('blob:fake')
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake')
    expect(document.body.contains(anchor)).toBe(false)
    expect(window.URL.createObjectURL.mock.calls[0][0].type).toBe('text/csv')
  })
})

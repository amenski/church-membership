import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/authStore'
import apiService from '@/services/api'

vi.mock('@/services/api', () => ({
  default: {
    logout: vi.fn().mockResolvedValue({}),
    getCurrentUser: vi.fn()
  }
}))

const ROLES = ['MEMBER', 'VOLUNTEER', 'STAFF', 'ADMIN']

function loginAs(store, role) {
  store.user = { role }
  store.isAuthenticated = true
}

describe('authStore', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useAuthStore()
  })

  describe('hasRole', () => {
    for (const userRole of ROLES) {
      for (const minRole of ROLES) {
        const expected = ROLES.indexOf(userRole) >= ROLES.indexOf(minRole)
        it(`${userRole} ${expected ? 'satisfies' : 'does not satisfy'} minimum ${minRole}`, () => {
          loginAs(store, userRole)
          expect(store.hasRole(minRole)).toBe(expected)
        })
      }
    }

    it('is false for every role when logged out', () => {
      store.user = { role: 'ADMIN' }
      store.isAuthenticated = false
      for (const minRole of ROLES) expect(store.hasRole(minRole)).toBe(false)
    })

    it('is false when the user role is unknown', () => {
      loginAs(store, 'SUPERUSER')
      expect(store.hasRole('MEMBER')).toBe(false)
    })

    it('is false when the minimum role is unknown', () => {
      loginAs(store, 'ADMIN')
      expect(store.hasRole('NOPE')).toBe(false)
    })

    it('is false when authenticated but user is null', () => {
      store.isAuthenticated = true
      expect(store.hasRole('MEMBER')).toBe(false)
    })
  })

  describe('role getters', () => {
    it.each([
      ['MEMBER', false, false, false],
      ['VOLUNTEER', true, false, false],
      ['STAFF', true, true, false],
      ['ADMIN', true, true, true]
    ])('%s: isVolunteer=%s isStaff=%s isAdmin=%s', (role, vol, staff, admin) => {
      loginAs(store, role)
      expect(store.isVolunteer).toBe(vol)
      expect(store.isStaff).toBe(staff)
      expect(store.isAdmin).toBe(admin)
    })

    it('are all false when logged out', () => {
      expect(store.isVolunteer).toBe(false)
      expect(store.isStaff).toBe(false)
      expect(store.isAdmin).toBe(false)
    })
  })

  describe('homePath', () => {
    it.each([
      ['VOLUNTEER', '/dashboard'],
      ['STAFF', '/dashboard'],
      ['ADMIN', '/dashboard'],
      ['MEMBER', '/profile']
    ])('%s -> %s', (role, path) => {
      loginAs(store, role)
      expect(store.homePath).toBe(path)
    })

    it('is /profile when logged out', () => {
      expect(store.homePath).toBe('/profile')
    })
  })

  describe('isSessionExpired', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-01-01T12:00:00Z'))
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('is false when the last activity is within the timeout', () => {
      loginAs(store, 'MEMBER')
      store.sessionTimeout = 60_000
      store.lastActivity = Date.now() - 59_000
      expect(store.isSessionExpired()).toBe(false)
    })

    it('is false exactly at the timeout boundary', () => {
      loginAs(store, 'MEMBER')
      store.sessionTimeout = 60_000
      store.lastActivity = Date.now() - 60_000
      expect(store.isSessionExpired()).toBe(false)
    })

    it('is true once the timeout has passed', () => {
      loginAs(store, 'MEMBER')
      store.sessionTimeout = 60_000
      store.lastActivity = Date.now() - 60_001
      expect(store.isSessionExpired()).toBe(true)
    })

    it('getTimeUntilExpiry counts down from the timeout', () => {
      loginAs(store, 'MEMBER')
      store.sessionTimeout = 60_000
      store.lastActivity = Date.now() - 20_000
      expect(store.getTimeUntilExpiry()).toBe(40_000)
    })

    it('is false when logged out even with stale activity', () => {
      store.sessionTimeout = 60_000
      store.lastActivity = Date.now() - 999_999
      expect(store.isSessionExpired()).toBe(false)
    })

    it('is false when there is no recorded activity', () => {
      loginAs(store, 'MEMBER')
      store.lastActivity = null
      expect(store.isSessionExpired()).toBe(false)
    })
  })

  describe('idle logout', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-01-01T12:00:00Z'))
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('logs out an idle user once the timeout has passed (monitor tick)', async () => {
      apiService.getCurrentUser.mockResolvedValue({ role: 'MEMBER' })
      store.setSessionTimeout(60_000)
      await store.checkAuth() // logs in and starts session monitoring
      expect(store.isAuthenticated).toBe(true)

      // No activity: just let the clock run past timeout + one 30 s monitor tick
      await vi.advanceTimersByTimeAsync(60_000 + 30_000)

      expect(apiService.logout).toHaveBeenCalled()
      expect(store.isAuthenticated).toBe(false)
    })
  })
})

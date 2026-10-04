import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// No network: the guard's checkAuth and logout both go through the api service.
vi.mock('@/services/api', () => ({
  default: {
    logout: vi.fn().mockResolvedValue({}),
    login: vi.fn(),
    post: vi.fn(),
    get: vi.fn()
  }
}))

// Fresh router + store per test so history/auth state cannot leak between cases.
async function setup({ role = null, lastActivity = null, sessionTimeout } = {}) {
  vi.resetModules()
  const { useAuthStore } = await import('@/stores/authStore')
  setActivePinia(createPinia())
  const store = useAuthStore()
  vi.spyOn(store, 'checkAuth').mockResolvedValue(undefined)
  if (role) {
    store.user = { role }
    store.isAuthenticated = true
    store.lastActivity = lastActivity ?? Date.now()
  }
  if (sessionTimeout) store.sessionTimeout = sessionTimeout
  const { useAppStore } = await import('@/stores/appStore')
  const appStore = useAppStore()
  const { default: router } = await import('@/router/index')
  return { router, store, appStore }
}

describe('router beforeEach guard', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/')
    window.scrollTo = vi.fn() // jsdom does not implement it; the router calls it
  })

  it('redirects a logged-out user from /members to login with redirect param', async () => {
    const { router } = await setup()
    await router.push('/members')
    const route = router.currentRoute.value
    expect(route.path).toBe('/login')
    expect(route.query).toEqual({ redirect: '/members' })
  })

  it('does not add a redirect param when logged out user wants /dashboard', async () => {
    const { router } = await setup()
    await router.push('/dashboard')
    expect(router.currentRoute.value.fullPath).toBe('/login')
  })

  it('sends a MEMBER away from /members to /profile with a notice and no query, without looping', async () => {
    const { router, appStore } = await setup({ role: 'MEMBER' })
    await router.push('/members')
    await router.isReady()
    const route = router.currentRoute.value
    expect(route.path).toBe('/profile')
    expect(route.query).toEqual({})
    expect(route.fullPath).toBe('/profile')
    expect(appStore.notifications).toHaveLength(1)
    expect(appStore.notifications[0]).toMatchObject({
      type: 'warning',
      title: 'Access denied',
      message: "You don't have access to that page."
    })
  })

  it('settles on /profile for MEMBER after the denial (no further redirect, one notice)', async () => {
    const { router, appStore } = await setup({ role: 'MEMBER' })
    await router.push('/payments')
    const first = router.currentRoute.value.fullPath
    await new Promise(r => setTimeout(r, 20))
    expect(router.currentRoute.value.fullPath).toBe(first)
    expect(first).toBe('/profile')
    expect(appStore.notifications).toHaveLength(1)
  })

  it('shows no notice when access is allowed', async () => {
    const { router, appStore } = await setup({ role: 'VOLUNTEER' })
    await router.push('/members')
    expect(appStore.notifications).toHaveLength(0)
  })

  it('sends a MEMBER at / to /profile', async () => {
    const { router } = await setup({ role: 'MEMBER' })
    await router.push('/')
    expect(router.currentRoute.value.fullPath).toBe('/profile')
  })

  it('sends a VOLUNTEER at / to /dashboard', async () => {
    const { router } = await setup({ role: 'VOLUNTEER' })
    await router.push('/')
    expect(router.currentRoute.value.fullPath).toBe('/dashboard')
  })

  it('sends a logged-in user at /login to their home', async () => {
    const { router } = await setup({ role: 'ADMIN' })
    await router.push('/login')
    expect(router.currentRoute.value.fullPath).toBe('/dashboard')
  })

  it('allows a VOLUNTEER into /members', async () => {
    const { router } = await setup({ role: 'VOLUNTEER' })
    await router.push('/members')
    expect(router.currentRoute.value.fullPath).toBe('/members')
  })

  it('sends STAFF away from /activity to their home with the Access denied notice', async () => {
    const { router, appStore } = await setup({ role: 'STAFF' })
    await router.push('/activity')
    expect(router.currentRoute.value.fullPath).toBe('/dashboard')
    expect(appStore.notifications).toHaveLength(1)
    expect(appStore.notifications[0]).toMatchObject({ type: 'warning', title: 'Access denied' })
  })

  it('allows ADMIN into /activity', async () => {
    const { router, appStore } = await setup({ role: 'ADMIN' })
    await router.push('/activity')
    expect(router.currentRoute.value.fullPath).toBe('/activity')
    expect(appStore.notifications).toHaveLength(0)
  })

  it('allows a MEMBER into /profile', async () => {
    const { router } = await setup({ role: 'MEMBER' })
    await router.push('/profile')
    expect(router.currentRoute.value.fullPath).toBe('/profile')
  })

  it('logs out and redirects to /login?session=expired when the session expired', async () => {
    const { router, store } = await setup({
      role: 'VOLUNTEER',
      lastActivity: Date.now() - 10_000,
      sessionTimeout: 1_000
    })
    const logoutSpy = vi.spyOn(store, 'logout').mockImplementation(async () => {
      // the real logout clears local auth state
      store.user = null
      store.isAuthenticated = false
    })
    await router.push('/members')
    expect(logoutSpy).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.fullPath).toBe('/login?session=expired')
  })

  it('calls checkAuth on navigation', async () => {
    const { router, store } = await setup()
    await router.push('/login')
    expect(store.checkAuth).toHaveBeenCalled()
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/services/api', () => ({
  default: { getCurrentUser: vi.fn().mockResolvedValue({ role: 'ADMIN', email: 'a@b.c' }), logout: vi.fn() }
}))

import App from '@/App.vue'
import { useAuthStore } from '@/stores/authStore'

describe('App drawer', () => {
  let wrapper

  beforeEach(() => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  })
  afterEach(() => wrapper?.unmount())

  const mountApp = async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    auth.user = { role: 'ADMIN', email: 'a@b.c' }
    auth.isAuthenticated = true
    auth.authChecked = true
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div>page</div>' } }]
    })
    router.push('/')
    await router.isReady()
    const i18n = createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false, messages: { en: {} } })
    wrapper = mount(App, { attachTo: document.body, global: { plugins: [pinia, router, i18n] } })
    await flushPromises()
  }

  const press = (key, init = {}) => {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init })
    document.activeElement.dispatchEvent(event)
    return event
  }

  it('keeps Tab inside the open drawer and makes the page behind it inert', async () => {
    await mountApp()
    await wrapper.get('button[aria-controls="appRail"]').trigger('click')
    await flushPromises()
    const rail = document.getElementById('appRail')
    expect(wrapper.get('main').element.hasAttribute('inert')).toBe(true)

    const focusable = [...rail.querySelectorAll('a[href], button:not([disabled])')]
    focusable[focusable.length - 1].focus()
    const forward = press('Tab')
    expect(forward.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(focusable[0])

    const back = press('Tab', { shiftKey: true })
    expect(back.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(focusable[focusable.length - 1])
  })

  it('leaves the page interactive while the drawer is closed', async () => {
    await mountApp()
    expect(wrapper.get('main').element.hasAttribute('inert')).toBe(false)
  })

  it('gives the Menu button a 44px touch target', async () => {
    await mountApp()
    expect(wrapper.get('button[aria-controls="appRail"]').classes()).toContain('min-h-11')
  })
})

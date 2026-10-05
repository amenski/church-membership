import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/services/api', () => ({
  default: { getCurrentUser: vi.fn().mockResolvedValue({ role: 'ADMIN', email: 'a@b.c' }), logout: vi.fn() }
}))

import App from '@/App.vue'
import { useAuthStore } from '@/stores/authStore'

describe('App bottom tabs', () => {
  let wrapper

  beforeEach(() => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  })
  afterEach(() => wrapper?.unmount())

  const mountApp = async ({ role = 'ADMIN', path = '/' } = {}) => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    auth.user = { role, email: 'a@b.c' }
    auth.isAuthenticated = true
    auth.authChecked = true
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div>page</div>' } }]
    })
    router.push(path)
    await router.isReady()
    wrapper = mount(App, { attachTo: document.body, global: { plugins: [pinia, router] } })
    await flushPromises()
  }

  const press = (key, init = {}) => {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init })
    document.activeElement.dispatchEvent(event)
    return event
  }

  it('renders the five tabs for a volunteer with aria-current on the active one', async () => {
    await mountApp({ role: 'VOLUNTEER', path: '/payments' })
    const tabs = wrapper.findAll('nav[aria-label="Main navigation"] a')
    expect(tabs.map(tab => tab.text())).toEqual(['Overview', 'Members', 'Payments', 'Messages', 'More'])
    expect(tabs.filter(tab => tab.attributes('aria-current')).map(tab => tab.text())).toEqual(['Payments'])
    expect(tabs[2].attributes('aria-current')).toBe('page')
    tabs.forEach(tab => expect(tab.classes()).toContain('h-[60px]'))
  })

  it('leaves the page interactive while the drawer is closed', async () => {
    await mountApp()
    expect(wrapper.get('main').element.hasAttribute('inert')).toBe(false)
  })

  it('marks More as aria-current="true" on a page it opens', async () => {
    await mountApp({ role: 'ADMIN', path: '/households' })
    const more = wrapper.findAll('nav[aria-label="Main navigation"] a').find(tab => tab.text() === 'More')
    expect(more.attributes('aria-current')).toBe('true')
  })
})

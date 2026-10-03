import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ToastHost from '@/components/ToastHost.vue'
import { useAppStore } from '@/stores/appStore'

describe('ToastHost', () => {
  let store
  let wrapper

  const mountHost = () => {
    wrapper = mount(ToastHost)
    return wrapper
  }

  beforeEach(() => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    store = useAppStore()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  const toasts = () => wrapper.findAll('[data-toast]')

  it('renders a notification with its title and message', async () => {
    mountHost()
    store.addNotification({ type: 'success', title: 'Send reminder', message: 'Reminder sent to Ann' })
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(1)
    expect(toasts()[0].text()).toContain('Send reminder')
    expect(toasts()[0].text()).toContain('Reminder sent to Ann')
    expect(toasts()[0].attributes('role')).toBe('status')
    expect(toasts()[0].attributes('aria-live')).toBe('polite')
  })

  it('announces errors as alerts', async () => {
    mountHost()
    store.addNotification({ type: 'error', title: 'Login failed', message: 'Wrong password' })
    await wrapper.vm.$nextTick()
    expect(toasts()[0].attributes('role')).toBe('alert')
  })

  it('shows notifications that were already in the store', () => {
    store.addNotification({ type: 'info', message: 'Already here' })
    mountHost()
    expect(toasts()).toHaveLength(1)
  })

  it('shows two notifications created in the same millisecond', async () => {
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'))
    mountHost()
    store.addNotification({ message: 'First' })
    store.addNotification({ message: 'Second' })
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(2)
  })

  it('dismisses on the close button and removes the notification from the store', async () => {
    mountHost()
    store.addNotification({ message: 'Close me' })
    await wrapper.vm.$nextTick()
    await wrapper.find('button[aria-label="Dismiss notification"]').trigger('click')
    expect(toasts()).toHaveLength(0)
    expect(store.notifications).toHaveLength(0)
  })

  it('dismisses itself after its duration', async () => {
    mountHost()
    store.addNotification({ message: 'Short', duration: 3000 })
    await wrapper.vm.$nextTick()
    vi.advanceTimersByTime(2900)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(1)
    vi.advanceTimersByTime(200)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(0)
  })

  it('pauses while hovered and continues with the time left', async () => {
    mountHost()
    store.addNotification({ message: 'Hover', duration: 4000 })
    await wrapper.vm.$nextTick()
    vi.advanceTimersByTime(3000)
    await toasts()[0].trigger('mouseenter')
    // the store's own timer has fired by now, the toast must still be there
    vi.advanceTimersByTime(60000)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(1)
    await toasts()[0].trigger('mouseleave')
    vi.advanceTimersByTime(900)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(1)
    vi.advanceTimersByTime(200)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(0)
  })

  it('pauses while focus is inside', async () => {
    mountHost()
    store.addNotification({ message: 'Focus', duration: 1000 })
    await wrapper.vm.$nextTick()
    await toasts()[0].trigger('focusin')
    vi.advanceTimersByTime(5000)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(1)
    await toasts()[0].trigger('focusout')
    vi.advanceTimersByTime(1100)
    await wrapper.vm.$nextTick()
    expect(toasts()).toHaveLength(0)
  })
})

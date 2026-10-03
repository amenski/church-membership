import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

describe('ConfirmDialog', () => {
  let wrapper

  afterEach(() => {
    wrapper?.unmount()
    document.body.style.overflow = ''
  })

  const mountDialog = (props = {}) => {
    wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: { modelValue: true, title: 'Send to 8 members?', message: 'They get it by email.', confirmLabel: 'Send to 8 members', ...props }
    })
    return wrapper
  }

  const dialog = () => document.body.querySelector('[role="dialog"]')
  const buttons = () => [...dialog().querySelectorAll('button')].filter(b => b.getAttribute('aria-label') !== 'Close')
  const byText = (text) => buttons().find(b => b.textContent.trim() === text)

  it('renders nothing while closed', () => {
    mountDialog({ modelValue: false })
    expect(dialog()).toBeNull()
  })

  it('shows the title, the message and both buttons', () => {
    mountDialog()
    const heading = document.getElementById(dialog().getAttribute('aria-labelledby'))
    expect(heading.textContent).toBe('Send to 8 members?')
    expect(dialog().textContent).toContain('They get it by email.')
    expect(byText('Cancel')).toBeTruthy()
    expect(byText('Send to 8 members')).toBeTruthy()
  })

  it('emits confirm from the confirm button only', async () => {
    mountDialog()
    byText('Cancel').click()
    expect(wrapper.emitted('confirm')).toBeUndefined()
    byText('Send to 8 members').click()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('asks the parent to close on Cancel', () => {
    mountDialog()
    byText('Cancel').click()
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('focuses Cancel when it opens, so Enter does not confirm', async () => {
    mountDialog({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(byText('Cancel'))
  })

  it('disables both buttons and marks the confirm button busy while busy', () => {
    mountDialog({ busy: true })
    expect(byText('Cancel').disabled).toBe(true)
    const confirm = byText('Send to 8 members')
    expect(confirm.disabled).toBe(true)
    expect(confirm.getAttribute('aria-busy')).toBe('true')
  })

  it('uses the danger style when asked', () => {
    mountDialog({ danger: true })
    expect(byText('Send to 8 members').className).toContain('tw:bg-clay')
  })
})

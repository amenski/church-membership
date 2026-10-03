import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseModal from '@/components/BaseModal.vue'

describe('BaseModal', () => {
  let wrapper
  let opener

  afterEach(() => {
    wrapper?.unmount()
    opener?.remove()
    document.body.style.overflow = ''
  })

  const mountModal = (props = {}) => {
    opener = document.createElement('button')
    opener.textContent = 'Open'
    document.body.appendChild(opener)
    opener.focus()
    wrapper = mount(BaseModal, {
      attachTo: document.body,
      props: { modelValue: true, title: 'Add member', ...props },
      slots: {
        default: '<label for="n">Name</label><input id="n" /><button id="inner">Inner</button>',
        footer: '<button id="save">Save</button>'
      }
    })
    return wrapper
  }

  const dialog = () => document.body.querySelector('[role="dialog"]')

  it('renders nothing while closed', () => {
    mountModal({ modelValue: false })
    expect(dialog()).toBeNull()
  })

  it('renders a labelled modal dialog when open', () => {
    mountModal()
    const el = dialog()
    expect(el.getAttribute('aria-modal')).toBe('true')
    const heading = document.getElementById(el.getAttribute('aria-labelledby'))
    expect(heading.textContent).toBe('Add member')
  })

  it('moves focus into the dialog and returns it to the opener on close', async () => {
    mountModal({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await wrapper.vm.$nextTick()
    expect(dialog().contains(document.activeElement)).toBe(true)
    await wrapper.setProps({ modelValue: false })
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(opener)
  })

  it('emits close on Escape', async () => {
    mountModal()
    dialog().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('emits close on a click on the backdrop but not inside the panel', async () => {
    mountModal()
    const backdrop = document.body.querySelector('[data-modal-backdrop]')
    dialog().dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    backdrop.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('keeps Tab inside the dialog', async () => {
    mountModal()
    await wrapper.vm.$nextTick()
    document.getElementById('save').focus()
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    dialog().dispatchEvent(tab)
    expect(tab.defaultPrevented).toBe(true)
    expect(dialog().contains(document.activeElement)).toBe(true)
    expect(document.activeElement).not.toBe(document.getElementById('save'))
  })

  it('locks body scroll while open and releases it on close', async () => {
    mountModal()
    expect(document.body.style.overflow).toBe('hidden')
    await wrapper.setProps({ modelValue: false })
    expect(document.body.style.overflow).toBe('')
  })
})

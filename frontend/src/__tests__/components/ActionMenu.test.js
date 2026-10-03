import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ActionMenu from '@/components/ActionMenu.vue'

const items = [
  { key: 'edit', label: 'Edit' },
  { key: 'off', label: 'Deactivate', disabled: true },
  { key: 'delete', label: 'Delete', danger: true }
]

describe('ActionMenu', () => {
  let wrapper

  afterEach(() => wrapper?.unmount())

  const mountMenu = () => {
    wrapper = mount(ActionMenu, {
      attachTo: document.body,
      props: { label: 'More actions for Sarah', items }
    })
    return wrapper
  }
  const trigger = () => wrapper.get('button[aria-haspopup="menu"]')
  const menu = () => document.body.querySelector('[role="menu"]')
  const menuItems = () => [...document.body.querySelectorAll('[role="menuitem"]')]
  const press = (el, key, init = {}) =>
    el.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init }))

  const clickTrigger = async (detail) => {
    trigger().element.dispatchEvent(new MouseEvent('click', { bubbles: true, detail }))
    await wrapper.vm.$nextTick()
  }

  it('is closed at first and labels its trigger', () => {
    mountMenu()
    expect(menu()).toBeNull()
    expect(trigger().attributes('aria-label')).toBe('More actions for Sarah')
    expect(trigger().attributes('aria-expanded')).toBe('false')
  })

  it('opens on click and lists the items as menuitems', async () => {
    mountMenu()
    await clickTrigger(1)
    expect(menu()).not.toBeNull()
    expect(trigger().attributes('aria-expanded')).toBe('true')
    expect(menuItems().map(el => el.textContent.trim())).toEqual(['Edit', 'Deactivate', 'Delete'])
    expect(menuItems()[1].disabled).toBe(true)
  })

  it('opens from the keyboard and focuses the first item', async () => {
    mountMenu()
    await clickTrigger(0)
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(menuItems()[0])
  })

  it('opens on ArrowDown with the first item focused and on ArrowUp with the last', async () => {
    mountMenu()
    await trigger().trigger('keydown', { key: 'ArrowDown' })
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(menuItems()[0])
    press(menu(), 'Escape')
    await wrapper.vm.$nextTick()
    await trigger().trigger('keydown', { key: 'ArrowUp' })
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(menuItems()[2])
  })

  it('moves with the arrow keys, wraps, skips disabled items, and supports Home and End', async () => {
    mountMenu()
    await trigger().trigger('keydown', { key: 'ArrowDown' })
    await wrapper.vm.$nextTick()
    press(menu(), 'ArrowDown')
    expect(document.activeElement).toBe(menuItems()[2])
    press(menu(), 'ArrowDown')
    expect(document.activeElement).toBe(menuItems()[0])
    press(menu(), 'ArrowUp')
    expect(document.activeElement).toBe(menuItems()[2])
    press(menu(), 'Home')
    expect(document.activeElement).toBe(menuItems()[0])
    press(menu(), 'End')
    expect(document.activeElement).toBe(menuItems()[2])
  })

  it('emits select with the key and closes', async () => {
    mountMenu()
    await clickTrigger(1)
    menuItems()[2].click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('select')).toEqual([['delete']])
    expect(menu()).toBeNull()
  })

  it('does not emit for a disabled item', async () => {
    mountMenu()
    await clickTrigger(1)
    menuItems()[1].click()
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('closes on Escape and returns focus to the trigger', async () => {
    mountMenu()
    await trigger().trigger('keydown', { key: 'ArrowDown' })
    await wrapper.vm.$nextTick()
    press(menu(), 'Escape')
    await wrapper.vm.$nextTick()
    expect(menu()).toBeNull()
    expect(document.activeElement).toBe(trigger().element)
  })

  it('closes on a press outside, without stealing focus', async () => {
    mountMenu()
    await clickTrigger(1)
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(menu()).toBeNull()
  })

  it('stays open for a press inside the menu', async () => {
    mountMenu()
    await clickTrigger(1)
    menu().dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(menu()).not.toBeNull()
  })

  it('closes on Tab and hands focus back to the trigger', async () => {
    mountMenu()
    await trigger().trigger('keydown', { key: 'ArrowDown' })
    await wrapper.vm.$nextTick()
    press(menu(), 'Tab')
    await wrapper.vm.$nextTick()
    expect(menu()).toBeNull()
    expect(document.activeElement).toBe(trigger().element)
  })

  it('toggles closed on a second click of the trigger', async () => {
    mountMenu()
    await clickTrigger(1)
    await clickTrigger(1)
    expect(menu()).toBeNull()
  })
})

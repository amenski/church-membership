import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseInput from '@/components/BaseInput.vue'

const mountInput = (props) => mount(BaseInput, { props: { id: 'f', label: 'Joined on', ...props } })

describe('BaseInput date helper', () => {
  it('shows a chosen date in the app format under a date input', () => {
    const wrapper = mountInput({ type: 'date', modelValue: '2026-10-04' })
    const helper = wrapper.find('#f-date')
    expect(helper.text()).toBe('Oct 4, 2026')
    expect(wrapper.find('input').attributes('aria-describedby')).toContain('f-date')
  })

  it('shows nothing while the date is empty', () => {
    const wrapper = mountInput({ type: 'date', modelValue: '' })
    expect(wrapper.find('#f-date').exists()).toBe(false)
  })

  it('shows nothing for other input types', () => {
    const wrapper = mountInput({ type: 'text', modelValue: '2026-10-04' })
    expect(wrapper.find('#f-date').exists()).toBe(false)
  })
})

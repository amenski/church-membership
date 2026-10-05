import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Pager from '@/components/Pager.vue'

const mountPager = (props) => mount(Pager, { props: { page: 1, pageSize: 25, ...props } })

describe('Pager', () => {
  it('is hidden when the whole list fits in the smallest page size', () => {
    expect(mountPager({ total: 10 }).find('nav').exists()).toBe(false)
  })

  it('keeps the Rows per page select but no buttons when the list fits the current size', () => {
    const wrapper = mountPager({ total: 12 })
    expect(wrapper.text()).toContain('Showing 1 to 12 of 12')
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.find('ul').exists()).toBe(false)
  })

  it('shows the select and the buttons when there is more than one page', () => {
    const wrapper = mountPager({ total: 60 })
    expect(wrapper.find('select').exists()).toBe(true)
    expect(wrapper.findAll('button[aria-label^="Page"]').map(b => b.text())).toEqual(['1', '2', '3'])
  })

  it('works from a given total: it emits the page asked for and the size chosen', async () => {
    const wrapper = mountPager({ total: 60 })
    await wrapper.find('button[aria-label="Page 2"]').trigger('click')
    await wrapper.find('select').setValue('50')
    expect(wrapper.emitted('update:page')).toEqual([[2]])
    expect(wrapper.emitted('update:pageSize')).toEqual([[50]])
  })
})

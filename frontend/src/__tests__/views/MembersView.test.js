import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RouterLinkStub, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MembersView from '@/views/MembersView.vue'
import api from '@/services/api'

vi.mock('@/services/api', () => ({ default: { getMembers: vi.fn(), getPaidMonths: vi.fn(() => Promise.resolve({})) } }))

const member = (over) => ({ id: 1, name: 'Abel', email: 'abel@example.com', phone: '', joinDate: '2026-01-05', status: 'MEMBER', consecutiveMonthsMissed: 0, ...over })

async function mountWith(members) {
  api.getMembers.mockResolvedValue(members)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(MembersView, { global: { plugins: [pinia], stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

// the name block is the first cell of the table row, and the first div of the card
const blocks = (wrapper) => [
  wrapper.find('tbody tr td:first-child'),
  wrapper.find('ul > li > div')
]

describe('MembersView contact line', () => {
  beforeEach(() => api.getMembers.mockReset())

  it('prints the email under the name', async () => {
    const wrapper = await mountWith([member()])
    for (const block of blocks(wrapper)) {
      expect(block.text()).toContain('abel@example.com')
    }
  })

  it('renders no empty line under the name when the member has no email', async () => {
    const wrapper = await mountWith([member({ email: null })])
    for (const block of blocks(wrapper)) {
      const lines = block.findAll(':scope > div').filter(d => d.classes().includes('text-muted'))
      expect(lines.filter(d => d.text() === '')).toHaveLength(0)
    }
  })
})

import { describe, expect, it } from 'vitest'
import en from '@/locales/en'
import am from '@/locales/am'

/** Every leaf of a catalogue as a dotted key, depth first. */
const keysOf = (node, prefix = '') =>
  Object.entries(node).flatMap(([key, value]) =>
    value && typeof value === 'object' ? keysOf(value, `${prefix}${key}.`) : [`${prefix}${key}`]
  )

const valueAt = (node, path) => path.split('.').reduce((at, key) => at?.[key], node)

// The two files are read side by side, so the contract is not just the same keys but the same order.
describe('the English and Amharic catalogues', () => {
  it('carry exactly the same keys, in the same order', () => {
    expect(keysOf(am)).toEqual(keysOf(en))
  })

  it('have no empty string', () => {
    const empty = keysOf(en).filter(key => {
      return String(valueAt(en, key)).trim() === '' || String(valueAt(am, key)).trim() === ''
    })
    expect(empty).toEqual([])
  })

})

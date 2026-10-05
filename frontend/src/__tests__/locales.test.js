import { describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
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

// vue-i18n compiles a message the first time it is asked for it, and one it cannot compile is reported
// to the console at runtime rather than failing the build: a nested placeholder such as '{{name}}' comes
// back as "Not allowed nest placeholder", and the surrounding sentence renders with the braces mangled.
// Compiling every key here catches that for keys no view happens to render in a test.
describe('every catalogue message', () => {
  it.each([['en', en], ['am', am]])('compiles in %s', (locale, messages) => {
    const logged = []
    const spies = ['warn', 'error'].map(level =>
      vi.spyOn(console, level).mockImplementation((...args) => logged.push(String(args[0])))
    )
    const i18n = createI18n({ legacy: false, locale, fallbackLocale: locale, messages: { [locale]: messages } })
    try {
      for (const key of keysOf(messages)) i18n.global.t(key)
    } finally {
      spies.forEach(spy => spy.mockRestore())
    }
    expect(logged.filter(message => /compilation error/i.test(message))).toEqual([])
  })

  // The two characters vue-i18n gives a meaning of its own: '@' opens a linked message and '{' opens a
  // placeholder. Both are escaped in the catalogues, and this is what the escapes have to render as.
  it('renders the escaped characters as themselves', () => {
    const i18n = createI18n({ legacy: false, locale: 'en', fallbackLocale: 'en', messages: { en } })

    expect(i18n.global.t('households.emailInvalid')).toContain('name@example.com')
    expect(i18n.global.t('messages.nameToken')).toBe('{{member_name}}')
  })
})

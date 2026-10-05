import { describe, it, expect } from 'vitest'
import { normalizeText, searchMembers } from '@/utils/memberSearch'

const members = [
  { id: 1, name: 'José Álvarez', email: 'jose@example.org', phone: '+251 911 22 33 44' },
  { id: 2, name: 'Mulu Tesfaye', email: 'mulu@example.org', phone: '0911-556677' },
  { id: 3, name: 'Tesfaye Kebede', email: '', phone: '' },
  { id: 4, name: 'Beyene Fortes', email: 'beyene@example.org', phone: '(555) 010 2000' },
  { id: 5, name: 'Abel Tesfahun', email: 'abel@tes.example.org' }
]
const names = (query) => searchMembers(members, query).map(member => member.name)

describe('normalizeText', () => {
  it('drops accents and case', () => {
    expect(normalizeText('José ÁLVAREZ')).toBe('jose alvarez')
  })
  it('turns missing values into an empty string', () => {
    expect(normalizeText(undefined)).toBe('')
    expect(normalizeText(null)).toBe('')
  })
})

describe('searchMembers', () => {
  it('returns everyone by name for a blank query', () => {
    expect(names('')).toEqual(['Abel Tesfahun', 'Beyene Fortes', 'José Álvarez', 'Mulu Tesfaye', 'Tesfaye Kebede'])
    expect(names('   ')).toHaveLength(5)
  })

  it('ignores case and accents in both directions', () => {
    expect(names('JOSE')).toEqual(['José Álvarez'])
    expect(names('álvarez')).toEqual(['José Álvarez'])
    expect(names('alvarez')).toEqual(['José Álvarez'])
  })

  it('matches the email', () => {
    expect(names('mulu@')).toEqual(['Mulu Tesfaye'])
  })

  it('needs every typed word to appear', () => {
    expect(names('tesfaye mulu')).toEqual(['Mulu Tesfaye'])
    expect(names('tesfaye zzz')).toEqual([])
  })

  it('ranks a name that starts with the query, then a name word that does, then the rest', () => {
    // Tesfaye Kebede starts with it, Abel Tesfahun and Mulu Tesfaye have a word that does, Beyene Fortes only contains it
    expect(names('tes')).toEqual(['Tesfaye Kebede', 'Abel Tesfahun', 'Mulu Tesfaye', 'Beyene Fortes'])
    expect(names('tesfaye')).toEqual(['Tesfaye Kebede', 'Mulu Tesfaye'])
  })

  it('breaks ties alphabetically whatever the input order', () => {
    const shuffled = [members[3], members[1], members[4], members[0], members[2]]
    expect(searchMembers(shuffled, 'tesf').map(member => member.name)).toEqual(['Tesfaye Kebede', 'Abel Tesfahun', 'Mulu Tesfaye'])
  })

  it('matches a phone by its digits, ignoring spaces, dashes, brackets and +', () => {
    expect(names('+251 911')).toEqual(['José Álvarez'])
    expect(names('0911556')).toEqual(['Mulu Tesfaye'])
    expect(names('555 010')).toEqual(['Beyene Fortes'])
    expect(names('911')).toEqual(['José Álvarez', 'Mulu Tesfaye'])
  })

  it('does not match a phone from text that is not a phone number', () => {
    expect(names('jose 911')).toEqual([])
    expect(names('911a')).toEqual([])
  })

  it('returns nothing for a query nobody matches, and copes with a missing list', () => {
    expect(names('nobody')).toEqual([])
    expect(searchMembers(undefined, 'x')).toEqual([])
  })

  it('does not change the list it was given', () => {
    const copy = members.map(member => member.id)
    searchMembers(members, 'tes')
    expect(members.map(member => member.id)).toEqual(copy)
  })
})

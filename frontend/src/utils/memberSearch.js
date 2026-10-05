// The member picker's search: which members match what was typed, best match first.
// Case and accents are ignored ("jose" finds "José"). Name and email match on text; a query made
// only of digits and phone punctuation also matches the phone, whatever its spaces, dashes or "+".

/** Lower case, accents removed, so "José" and "jose" compare equal. */
export function normalizeText(value) {
  return String(value ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

const PHONE_QUERY = /^[\d\s+().-]+$/
const digitsOf = (value) => String(value ?? '').replace(/\D/g, '')

// 0: the name starts with what was typed, 1: every typed word starts a word of the name, 2: it is somewhere else
function rank(name, tokens, query) {
  if (name.startsWith(query)) return 0
  const words = name.split(/\s+/)
  return tokens.every(token => words.some(word => word.startsWith(token))) ? 1 : 2
}

/**
 * The members that match `query`, best first: prefix matches on the name, then the rest, ties by
 * name. Every word typed must appear in the name or email. A blank query returns everyone by name.
 * Does not change the input.
 * @param {{ id: number|string, name: string, email?: string, phone?: string }[]} members
 * @param {string} query
 */
export function searchMembers(members, query) {
  const list = Array.isArray(members) ? members : []
  const text = normalizeText(query).trim().replace(/\s+/g, ' ')
  const tokens = text ? text.split(' ') : []
  const phoneDigits = PHONE_QUERY.test(text) ? digitsOf(text) : ''
  const scored = []
  for (const member of list) {
    const name = normalizeText(member.name)
    let score = 0
    if (tokens.length) {
      const haystack = `${name} ${normalizeText(member.email)}`
      if (tokens.every(token => haystack.includes(token))) score = rank(name, tokens, text)
      else if (phoneDigits && digitsOf(member.phone).includes(phoneDigits)) score = 2
      else continue
    }
    scored.push({ member, name, score })
  }
  scored.sort((a, b) => a.score - b.score || a.name.localeCompare(b.name) || String(a.member.id).localeCompare(String(b.member.id)))
  return scored.map(entry => entry.member)
}

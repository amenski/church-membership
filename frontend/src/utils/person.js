// Body of POST/PUT /api/people (PersonRequest). A blank optional field is sent as null: PUT replaces
// every field. householdId is sent on create only; an edit leaves it out, so the household is kept.
export function buildPersonRequest(form, householdId) {
  const request = {
    name: (form.name || '').trim(),
    email: (form.email || '').trim() || null,
    phone: (form.phone || '').trim() || null,
    birthDate: form.birthDate || null
  }
  if (householdId != null) request.householdId = householdId
  return request
}

// Body of POST /api/people/{id}/membership (MembershipRequest): a blank join date means today on the server
export function buildMembershipRequest(form) {
  const request = { status: form.status || 'MEMBER' }
  if (form.joinDate) request.joinDate = form.joinDate
  return request
}

/** "11 years old" from a YYYY-MM-DD birth date (read as a calendar day, no time zone); '' when unknown or in the future. */
export function ageText(birthDate, now = new Date()) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(birthDate || '')
  if (!match) return ''
  const [year, month, day] = match.slice(1).map(Number)
  const birthdayPassed = now.getMonth() + 1 > month || (now.getMonth() + 1 === month && now.getDate() >= day)
  const years = now.getFullYear() - year - (birthdayPassed ? 0 : 1)
  if (years < 0) return ''
  if (years < 1) return 'under 1 year old'
  return `${years} ${years === 1 ? 'year' : 'years'} old`
}

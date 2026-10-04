// Body of POST/PUT /api/members (MemberRequest). Only these fields are sent:
// id, counters and payment dates are managed by the server. The status is sent as `status`;
// the legacy `active` flag is no longer sent.
export function buildMemberRequest(form) {
  const request = {
    name: (form.name || '').trim()
  }
  const email = (form.email || '').trim()
  if (email) request.email = email
  const phone = (form.phone || '').trim()
  if (phone) request.phone = phone
  if (form.joinDate) request.joinDate = form.joinDate
  request.status = form.status || 'MEMBER'
  // The household is sent only when the user touched that field (an explicit null leaves the household).
  // Without it the server keeps the household, so "Mark inactive" never clears it.
  if (form.householdTouched) request.householdId = form.householdId ? Number(form.householdId) : null
  return request
}

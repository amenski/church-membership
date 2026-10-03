// Body of POST/PUT /api/members (MemberRequest). Only these fields are sent:
// id, counters and payment dates are managed by the server.
export function buildMemberRequest(form) {
  const request = {
    name: (form.name || '').trim(),
    email: (form.email || '').trim()
  }
  const phone = (form.phone || '').trim()
  if (phone) request.phone = phone
  if (form.joinDate) request.joinDate = form.joinDate
  request.active = Boolean(form.active)
  return request
}

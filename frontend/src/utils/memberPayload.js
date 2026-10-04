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
  return request
}

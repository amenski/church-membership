// Same pattern as @Pattern on UpdateUserProfileRequest.phone on the server. Keep the two in step.
export const PHONE_PATTERN = /^\+?[0-9\s\-()]{10,}$/

/** A blank phone is valid (it clears the phone); otherwise it must match the server pattern. */
export function isValidPhone(phone) {
  if (phone == null || String(phone).trim() === '') return true
  return PHONE_PATTERN.test(phone)
}

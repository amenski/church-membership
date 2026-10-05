// Mirrors User.validatePasswordStrength on the server. Keep the two in step.
// The words live in locales/ (validation.passwordRule): this module only decides pass or fail.
export const PASSWORD_RULE_KEY = 'validation.passwordRule'

const LOWER = /\p{Ll}/u
const UPPER = /\p{Lu}/u
const DIGIT = /\p{Nd}/u
// special = anything that is not a letter, a digit or whitespace
const SPECIAL = /[^\p{L}\p{N}\s]/u

/** Returns '' when the password passes the server rule, otherwise the i18n key of the message to show. */
export function validateNewPassword(pw) {
  if (typeof pw !== 'string') return PASSWORD_RULE_KEY
  const bytes = new TextEncoder().encode(pw).length
  const ok =
    pw.length >= 8 && bytes <= 72 && LOWER.test(pw) && UPPER.test(pw) && DIGIT.test(pw) && SPECIAL.test(pw)
  return ok ? '' : PASSWORD_RULE_KEY
}

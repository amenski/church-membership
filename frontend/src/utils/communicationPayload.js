// Body of the communication write endpoints (SendCommunicationRequest).
export function buildCommunicationRequest(form) {
  return {
    title: form.subject.trim(),
    messageContent: form.message.trim()
  }
}

// Body of the dashboard "Send Reminder" action.
export function buildReminderRequest(member) {
  return {
    title: 'Payment reminder',
    messageContent: `Dear ${member.name}, this is a reminder that your membership payment is overdue.`
  }
}

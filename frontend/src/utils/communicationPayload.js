// Body of the communication write endpoints (SendCommunicationRequest).
export function buildCommunicationRequest(form) {
  return {
    title: form.subject.trim(),
    messageContent: form.message.trim()
  }
}

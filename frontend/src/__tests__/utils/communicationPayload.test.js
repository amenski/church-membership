import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildCommunicationRequest, buildReminderRequest } from '@/utils/communicationPayload'

// Shared with the backend CommunicationContractTest.
// vitest runs from frontend/ (jsdom makes import.meta.url a non-file URL)
const fixturePath = resolve(process.cwd(), '../src/test/resources/contracts/send-communication-request.json')
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))

describe('buildCommunicationRequest', () => {
  it('matches the shared contract fixture', () => {
    const form = { subject: 'Payment reminder ', message: ' Dear member, your payment is overdue.' }
    expect(buildCommunicationRequest(form)).toEqual(fixture)
  })

  it('trims blank input to empty strings', () => {
    expect(buildCommunicationRequest({ subject: '  ', message: '  ' })).toEqual({ title: '', messageContent: '' })
  })
})

describe('buildReminderRequest', () => {
  it('personalises the reminder and has no extra keys', () => {
    const request = buildReminderRequest({ name: 'Abel' })
    expect(request.title).toBe('Payment reminder')
    expect(request.messageContent).toContain('Abel')
    expect(request.title.length).toBeGreaterThan(0)
    expect(request.messageContent.length).toBeGreaterThan(0)
    expect(Object.keys(request)).toEqual(['title', 'messageContent'])
  })
})

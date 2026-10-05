# Email and Communications

*Last checked against the code: 5 October 2026.*

How MemberTracker sends email to members, and how to configure and test it.

## What gets sent

| Trigger | Use case | Recipients | Method |
|---------|----------|------------|--------|
| `POST /api/communications/send-to-all` | `SendCommunicationToAllMembersUseCase` | All members with status MEMBER and an email | Plain text, with retry |
| `POST /api/communications/send-to-overdue/{months}` | `SendCommunicationToMembersUseCase` | MEMBER-status members overdue by at least `months` | Plain text, with retry |
| `POST /api/communications/send-to-member/{memberId}` | `SendCommunicationToMembersUseCase` | One member (path `memberId`) | Plain text, with retry |
| Monthly reminder job (1st, 09:00) | `SendPaymentRemindersUseCase` (`app.payment.reminder.months-threshold` months missed, default 3) | MEMBER-status members at or over the threshold | Plain text, with retry. Runs only while the application is up at that time; see [features/payment-reminder-scheduler.md](features/payment-reminder-scheduler.md). |

Each send runs on a background thread from `Executors.newCachedThreadPool()`, with a 100 ms pause between emails to stay under SMTP rate limits. A send still in progress is lost if the app restarts.

`EmailService` sends plain text only. HTML email templates and their Thymeleaf methods existed but nothing called them; they were removed in `chore: remove the unused mail templates and email methods` and can be recovered from git history.

### Delivery tracking

Each recipient gets a `MessageDelivery` row:

- **Channel:** `EMAIL`, `SMS` or `WHATSAPP`. SMS and WhatsApp are stubs that record a failed delivery.
- **Status:** `PENDING`, `SENT`, `FAILED` or `DELIVERED`
- **Also stored:** timestamp, failure notes and `attempts`, the number of send attempts made (migration `008.add-message-delivery-attempts.sql`)

The rows are written when the communication is saved (status `PENDING`) and each one is updated as soon as its email result is known. The send response itself does not list them: to see deliveries, call `GET /api/communications/{id}/deliveries`, or open the delivery dialog on the Messages page.

`attempts` counts the tries the mail service made for that recipient: 1 when the first try worked, up to `app.mail.retry.max-attempts` (3 by default) when it kept failing. It stays 0 when no try was made (mail disabled, SMS and WhatsApp stubs, rows from before the migration). The send use cases count them with the `RetryCallback` of `EmailService.sendSimpleEmailWithRetry` (`usecase/SendCommunicationToAllMembersUseCase.java`, `usecase/SendCommunicationToMembersUseCase.java`) and store the number together with the status. The Messages screen shows it beside the note as "3 attempts".

STAFF can retry a `FAILED` email delivery from the delivery dialog (`POST /api/communications/{id}/deliveries/{deliveryId}/retry`). It re-sends once, synchronously (one full retry cycle, so up to the maximum attempts again), and ADDS the attempts it made to the stored count (`usecase/RetryDeliveryUseCase.java`): a delivery that failed 3 times and then succeeded on the second try of a manual retry shows 5.

## Who gets a message

Members without an email address are skipped, and members who share an address get one message between them (the one with the lowest id; `usecase/Recipients.java`). A message to a single member who has no email is refused with a 400.

## Personalisation

`{{member_name}}` in a communication's title or message is replaced with each recipient's name when the email is sent: bulk send, send to selected members, send-to-overdue, the monthly reminder job and a delivery retry. A blank or missing name becomes "member". The stored communication keeps the placeholder; only the emailed text is personalised (`utils/MessageTemplates.java`). Other text is left as written.

## Configuration

In `src/main/resources/application.properties`. Each value can be overridden by the environment variable shown.

```properties
app.mail.enabled=true
app.mail.from=${MAIL_FROM:noreply@church.example.com}
app.mail.smtp.host=${MAIL_HOST:smtp.gmail.com}
app.mail.smtp.port=${MAIL_PORT:587}
app.mail.smtp.username=${MAIL_USERNAME:}
app.mail.smtp.password=${MAIL_PASSWORD:}
app.mail.smtp.auth=${MAIL_AUTH:true}
app.mail.smtp.starttls-enable=${MAIL_STARTTLS:true}
app.mail.smtp.debug=${MAIL_DEBUG:false}

# Retry for every email send (exponential backoff)
app.mail.retry.max-attempts=${MAIL_RETRY_MAX_ATTEMPTS:3}
app.mail.retry.initial-delay-ms=${MAIL_RETRY_INITIAL_DELAY_MS:1000}
app.mail.retry.multiplier=${MAIL_RETRY_MULTIPLIER:2.0}
```

For Gmail with 2-step verification, use an app password. Never commit real SMTP credentials.

The STARTTLS key is `app.mail.smtp.starttls-enable` (a hyphen). The earlier dotted spelling `starttls.enable` never bound, so `MAIL_STARTTLS=false` and the dev profile's `false` were ignored and STARTTLS always stayed on (`MailPropertiesBindingTest` guards this).

### Local testing with MailHog

MailHog catches all mail locally so nothing reaches real members.

```bash
brew install mailhog && mailhog     # web UI at http://localhost:8025
```

Then in `application-dev.properties`:

```properties
app.mail.from=dev-noreply@localhost
app.mail.smtp.host=localhost
app.mail.smtp.port=1025
app.mail.smtp.auth=false
app.mail.smtp.starttls-enable=false
app.mail.smtp.debug=true
```

## Send a test message

Use the Messages page, or log in with curl first (see [authentication.md](authentication.md#manual-test)):

```bash
curl -b jar.txt -X POST http://localhost:8080/api/communications/send-to-all \
  -H 'Content-Type: application/json' \
  -d '{"title":"Test","messageContent":"Test email from MemberTracker","type":"ANNOUNCEMENT"}'
```

This needs the STAFF role or higher.

## Troubleshooting

Turn on debug logging:

```properties
logging.level.io.github.membertracker.infrastructure.service.EmailService=DEBUG
logging.level.org.springframework.mail=DEBUG
```

| Symptom | Check |
|---------|-------|
| Authentication failed | SMTP username and password; for Gmail, an app password |
| Connection refused | Host and port. Test with `nc -vz <host> <port>`. |
| Nothing sent | `app.mail.enabled=true`; look for errors from `EmailService` in the log |

## Known gaps

An empty audience (no member with status MEMBER and an email, or none behind by the given months) is a 400 `COMMUNICATION_006` and nothing is stored. `GET /api/communications` returns, per message, `recipientCount` and a `deliverySummary` (`sent`, `failed`, `pending`, `delivered`) counted by one grouped query.

Tracked in [functionality-audit.md](functionality-audit.md) (C3, C4) and [todo.md](todo.md).

- No unsubscribe link or consent record
- Only two audiences: everyone, or members who are behind (plus one member)
- No durable queue
- SMS and WhatsApp are not implemented

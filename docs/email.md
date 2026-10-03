# Email and Communications

*Last checked against the code: 3 October 2026.*

How MemberTracker sends email to members, and how to configure and test it.

## What gets sent

| Trigger | Use case | Recipients | Method |
|---------|----------|------------|--------|
| `POST /api/communications/send-to-all` | `SendCommunicationToAllMembersUseCase` | All **active** members | Plain text, with retry |
| `POST /api/communications/send-to-overdue/{months}` | `SendCommunicationToMembersUseCase` | Members overdue by at least `months` | Plain text, no retry |
| `POST /api/communications/send-to-member/{memberId}` | `SendCommunicationToMembersUseCase` | One member (path `memberId`) | Plain text, no retry |
| Monthly reminder job (1st, 09:00) | `SendPaymentRemindersUseCase` (`app.payment.reminder.months-threshold` months missed, default 3) | Active members at or over the threshold | Plain text, no retry. Runs only while the application is up at that time; see [features/payment-reminder-scheduler.md](features/payment-reminder-scheduler.md). |

Each send runs on a background thread from `Executors.newCachedThreadPool()`, with a 100 ms pause between emails to stay under SMTP rate limits. A send still in progress is lost if the app restarts.

`EmailService` also has HTML template methods (`sendPaymentReminder`, `sendWelcomeEmail`, `sendAnnouncement`), which use the Thymeleaf templates in `src/main/resources/templates/emails/`. **Nothing calls them yet.**

### Delivery tracking

Each recipient gets a `MessageDelivery` row:

- **Channel:** `EMAIL`, `SMS` or `WHATSAPP`. SMS and WhatsApp are stubs that record a failed delivery.
- **Status:** `PENDING`, `SENT`, `FAILED` or `DELIVERED`
- **Also stored:** timestamp and failure notes

To see deliveries, call `GET /api/communications/{id}/deliveries`, or open the delivery dialog on the Communications page.

STAFF can retry a `FAILED` email delivery from the delivery dialog (`POST /api/communications/{id}/deliveries/{deliveryId}/retry`). It re-sends once, synchronously.

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
app.mail.smtp.starttls.enable=${MAIL_STARTTLS:true}
app.mail.smtp.debug=${MAIL_DEBUG:false}

# Retry for send-to-all (exponential backoff)
app.mail.retry.max-attempts=${MAIL_RETRY_MAX_ATTEMPTS:3}
app.mail.retry.initial-delay-ms=${MAIL_RETRY_INITIAL_DELAY_MS:1000}
app.mail.retry.multiplier=${MAIL_RETRY_MULTIPLIER:2.0}

# Shown in emails
app.church.name=${CHURCH_NAME:Felege Selam Church}
app.church.contact.phone=${CHURCH_PHONE:...}
app.church.contact.email=${CHURCH_EMAIL:office@church.example.com}
```

For Gmail with 2-step verification, use an app password. Never commit real SMTP credentials.

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
app.mail.smtp.starttls.enable=false
app.mail.smtp.debug=true
```

## Send a test message

Use the Communications page, or log in with curl first (see [authentication.md](authentication.md#manual-test)):

```bash
curl -b jar.txt -X POST http://localhost:8080/api/communications/send-to-all \
  -H 'Content-Type: application/json' \
  -d '{"title":"Test","messageContent":"Test email from MemberTracker","type":"ANNOUNCEMENT"}'
```

This needs the STAFF role or higher.

## Adding a template

1. Add `src/main/resources/templates/emails/<name>.html`. Thymeleaf HTML-escapes the variables you pass in.
2. Add the template name to `MailProperties.Templates` and `application.properties`.
3. Add a method to `EmailService` that calls `sendTemplatedEmail(...)`.

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
| Template not found | The file is in `templates/emails/` and its name matches `MailProperties` |

## Known gaps

Tracked in [functionality-audit.md](functionality-audit.md) (C3, C4) and [todo.md](todo.md).

- No unsubscribe link or consent record
- Only two audiences: everyone, or overdue members
- No durable queue
- SMS and WhatsApp are not implemented

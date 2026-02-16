# Email Integration Guide

## Overview

The MemberTracker application now includes email integration for sending communications to members. This includes:
- Payment reminders
- Welcome emails for new members
- General announcements
- Custom communications

## Configuration

### Application Properties

Email configuration is managed through Spring Boot properties:

```properties
# Enable/disable email functionality
app.mail.enabled=true

# Sender email address
app.mail.from=noreply@church.example.com

# SMTP Configuration
app.mail.smtp.host=smtp.gmail.com
app.mail.smtp.port=587
app.mail.smtp.username=your-email@gmail.com
app.mail.smtp.password=your-app-password
app.mail.smtp.auth=true
app.mail.smtp.starttls.enable=true
app.mail.smtp.debug=false

# Email template names
app.mail.templates.payment-reminder=payment-reminder
app.mail.templates.welcome=welcome
app.mail.templates.announcement=announcement
```

### Development Configuration

For development, use MailHog (a fake SMTP server):

1. Install MailHog:
   ```bash
   # macOS
   brew install mailhog
   
   # Linux
   wget https://github.com/mailhog/MailHog/releases/download/v1.0.1/MailHog_linux_amd64
   chmod +x MailHog_linux_amd64
   sudo mv MailHog_linux_amd64 /usr/local/bin/mailhog
   ```

2. Start MailHog:
   ```bash
   mailhog
   ```

3. Access the web interface at `http://localhost:8025`

4. Update `application-dev.properties`:
   ```properties
   app.mail.enabled=true
   app.mail.from=dev-noreply@localhost
   app.mail.smtp.host=localhost
   app.mail.smtp.port=1025
   app.mail.smtp.auth=false
   app.mail.smtp.starttls.enable=false
   app.mail.smtp.debug=true
   ```

### Production Configuration

For production, use environment variables:

```bash
export MAIL_FROM=noreply@yourchurch.org
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export MAIL_AUTH=true
export MAIL_STARTTLS=true
```

## Email Templates

The application uses Thymeleaf templates located in `src/main/resources/templates/emails/`:

### Available Templates

1. **payment-reminder.html** - For overdue payment notifications
   - Variables: `member`, `monthsMissed`, `paymentAmount`, `dueDate`, `churchName`

2. **welcome.html** - For new member welcome emails
   - Variables: `member`, `welcomeMessage`, `churchName`

3. **announcement.html** - For general announcements
   - Variables: `member`, `announcementTitle`, `announcementContent`, `churchName`

### Creating Custom Templates

1. Create a new HTML file in `src/main/resources/templates/emails/`
2. Add Thymeleaf attributes as needed
3. Update `MailProperties.Templates` class to include the new template name
4. Add a corresponding method in `EmailService`

## API Integration

### How It Works

1. **SendCommunicationToAllMembersUseCase** - Sends emails to all members asynchronously
2. **SendCommunicationToMembersUseCase** - Sends emails to specific members asynchronously
3. **EmailService** - Handles SMTP connection and template rendering

### Delivery Status Tracking

Each email sent is tracked with a `MessageDelivery` record that includes:
- Recipient information
- Delivery channel (EMAIL, SMS, WHATSAPP)
- Status (PENDING, SENT, FAILED, DELIVERED)
- Delivery timestamp
- Response notes (for failures)

### Asynchronous Processing

Emails are sent asynchronously using Java 21+ virtual threads:
- Prevents blocking the main application thread
- Allows for retry logic and error handling
- Provides better performance for bulk emails

## Testing Email Functionality

### Test Endpoint

To test email configuration, you can:

1. Use the Communications page in the web interface
2. Send a test communication to all members or specific members

### Manual Testing via API

```bash
# Create a test communication
curl -X POST http://localhost:8080/api/communications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "title": "Test Email",
    "messageContent": "This is a test email from MemberTracker",
    "type": "ANNOUNCEMENT"
  }'
```

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Ensure SMTP username and password are correct
   - For Gmail, use an App Password if 2FA is enabled
   - Check if `app.mail.smtp.auth` is set to `true`

2. **Connection Refused**
   - Verify SMTP host and port are correct
   - Check firewall settings
   - Test with `telnet <host> <port>`

3. **Emails Not Sending**
   - Check `app.mail.enabled` is `true`
   - Verify email service is initialized (check logs)
   - Look for exceptions in application logs

4. **Templates Not Found**
   - Ensure template files are in `src/main/resources/templates/emails/`
   - Check template names in `MailProperties`

### Logging

Enable debug logging for email-related issues:

```properties
logging.level.io.github.membertracker.infrastructure.service.EmailService=DEBUG
logging.level.org.springframework.mail=DEBUG
logging.level.com.sun.mail=DEBUG
```

## Security Considerations

1. **Credentials Storage**
   - Never commit email passwords to version control
   - Use environment variables or secret management
   - Rotate credentials regularly

2. **Rate Limiting**
   - The system includes a 100ms delay between emails to avoid SMTP rate limits
   - Consider implementing additional rate limiting for bulk sends

3. **Content Validation**
   - All user-generated content in emails is HTML-escaped by Thymeleaf
   - Consider additional sanitization for user-provided templates

## Future Enhancements

Planned improvements:
1. SMS integration via Twilio/MessageBird
2. WhatsApp Business API integration
3. Email scheduling and queue management
4. Advanced template editor in UI
5. Email analytics and open/click tracking
6. Attachment support for emails

## Monitoring

Key metrics to monitor:
- Email send success rate
- Average delivery time
- Failure reasons and patterns
- SMTP connection health

Check application logs for email-related events and errors.
package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.CommunicationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the communication write endpoints. Only these fields come from the client. */
public class SendCommunicationRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    private String messageContent;

    /** Optional, JSON code (ANNOUNCEMENT, REMINDER, PERSONAL); absent means ANNOUNCEMENT. */
    private CommunicationType type;

    public SendCommunicationRequest() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public CommunicationType getType() {
        return type;
    }

    public void setType(CommunicationType type) {
        this.type = type;
    }
}

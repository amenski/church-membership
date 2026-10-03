package io.github.membertracker.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.exception.CommunicationDomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Communication {

    private Long id;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message content must not exceed 5000 characters")
    private String messageContent;
    private LocalDateTime createdDate;
    private LocalDateTime sentDate;

    @Enumerated(EnumType.STRING)
    private CommunicationType type;

    private boolean sentToAllMembers;

    private List<MessageDelivery> deliveries = new ArrayList<>();

    // Derived from the deliveries when a communication is read or saved; not stored on the communication
    private int recipientCount;
    private DeliverySummary deliverySummary = DeliverySummary.EMPTY;

    // Constructors
    public Communication() {
        this.createdDate = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getSentDate() {
        return sentDate;
    }

    public void setSentDate(LocalDateTime sentDate) {
        this.sentDate = sentDate;
    }

    public CommunicationType getType() {
        return type;
    }

    public void setType(CommunicationType type) {
        this.type = type;
    }

    public boolean isSentToAllMembers() {
        return sentToAllMembers;
    }

    public void setSentToAllMembers(boolean sentToAllMembers) {
        this.sentToAllMembers = sentToAllMembers;
    }

    /** Not serialised: a delivery points back to its communication, and deliveries have their own endpoint. */
    @JsonIgnore
    public List<MessageDelivery> getDeliveries() {
        return deliveries;
    }

    public void setDeliveries(List<MessageDelivery> deliveries) {
        this.deliveries = deliveries;
    }

    /** Number of deliveries (recipients); 0 when nothing was sent. Not persisted. */
    public int getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(int recipientCount) {
        this.recipientCount = recipientCount;
    }

    /** Deliveries per status; all zero when nothing was sent. Not persisted. */
    public DeliverySummary getDeliverySummary() {
        return deliverySummary;
    }

    public void setDeliverySummary(DeliverySummary deliverySummary) {
        this.deliverySummary = deliverySummary == null ? DeliverySummary.EMPTY : deliverySummary;
    }

    // Behaviour
    public boolean isSent() {
        return sentDate != null;
    }

    public void markAsSent() {
        if (isSent()) {
            throw new CommunicationDomainException(
                    "Communication has already been sent", CommunicationDomainException.ALREADY_SENT);
        }
        this.sentDate = LocalDateTime.now();
    }

    public void addDelivery(MessageDelivery delivery) {
        if (deliveries == null) {
            deliveries = new ArrayList<>();
        }
        deliveries.add(delivery);
    }
}

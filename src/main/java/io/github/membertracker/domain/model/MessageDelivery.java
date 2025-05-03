package io.github.membertracker.domain.model;

import java.time.LocalDateTime;

public class MessageDelivery {
    private Long id;
    private Member recipient;
    private Communication communication;
    private LocalDateTime deliveryTime;
    private DeliveryStatus status;
    private DeliveryChannel channel;
    private String responseNotes;

    // Enums
    public enum DeliveryStatus {
        PENDING, SENT, FAILED, DELIVERED
    }

    public enum DeliveryChannel {
        EMAIL, SMS, WHATSAPP
    }

    // Constructors
    public MessageDelivery() {
    }

    public MessageDelivery(Member recipient, Communication communication, DeliveryChannel channel) {
        this.recipient = recipient;
        this.communication = communication;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Member getRecipient() {
        return recipient;
    }

    public void setRecipient(Member recipient) {
        this.recipient = recipient;
    }

    public Communication getCommunication() {
        return communication;
    }

    public void setCommunication(Communication communication) {
        this.communication = communication;
    }

    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public DeliveryChannel getChannel() {
        return channel;
    }

    public void setChannel(DeliveryChannel channel) {
        this.channel = channel;
    }

    public String getResponseNotes() {
        return responseNotes;
    }

    public void setResponseNotes(String responseNotes) {
        this.responseNotes = responseNotes;
    }
}

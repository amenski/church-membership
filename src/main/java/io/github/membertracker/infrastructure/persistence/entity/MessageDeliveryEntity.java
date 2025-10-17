package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_delivery")
public class MessageDeliveryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private MemberEntity recipient;

    @ManyToOne
    private CommunicationEntity communication;

    private LocalDateTime deliveryTime;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    @Enumerated(EnumType.STRING)
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
    public MessageDeliveryEntity() {
    }

    public MessageDeliveryEntity(MemberEntity recipient, CommunicationEntity communication, DeliveryChannel channel) {
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

    public MemberEntity getRecipient() {
        return recipient;
    }

    public void setRecipient(MemberEntity recipient) {
        this.recipient = recipient;
    }

    public CommunicationEntity getCommunication() {
        return communication;
    }

    public void setCommunication(CommunicationEntity communication) {
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
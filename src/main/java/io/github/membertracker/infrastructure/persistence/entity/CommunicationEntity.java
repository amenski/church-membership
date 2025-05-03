package io.github.membertracker.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class CommunicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String messageContent;
    private LocalDateTime createdDate;
    private LocalDateTime sentDate;

    @Enumerated(EnumType.STRING)
    private CommunicationEntity.CommunicationType type;

    private boolean sentToAllMembers;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "communication")
    private List<MessageDeliveryEntity> deliveries = new ArrayList<>();

    // Enum for communication types
    public enum CommunicationType {
        ANNOUNCEMENT, REMINDER, PERSONAL
    }

    // Constructors
    public CommunicationEntity() {
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

    public CommunicationEntity.CommunicationType getType() {
        return type;
    }

    public void setType(CommunicationEntity.CommunicationType type) {
        this.type = type;
    }

    public boolean isSentToAllMembers() {
        return sentToAllMembers;
    }

    public void setSentToAllMembers(boolean sentToAllMembers) {
        this.sentToAllMembers = sentToAllMembers;
    }

    public List<MessageDeliveryEntity> getDeliveries() {
        return deliveries;
    }

    public void setDeliveries(List<MessageDeliveryEntity> deliveries) {
        this.deliveries = deliveries;
    }
}

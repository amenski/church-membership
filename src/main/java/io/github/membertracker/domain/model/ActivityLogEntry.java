package io.github.membertracker.domain.model;

import io.github.membertracker.domain.enumeration.ActivityType;

import java.time.LocalDateTime;

/** One line of the audit trail: who did what, and to which record. Descriptions carry names, never emails or phones. */
public class ActivityLogEntry {
    private Long id;
    private ActivityType type;
    private String description;
    private String entityType;
    private Long entityId;
    private String actor;
    private LocalDateTime createdAt;

    public ActivityLogEntry() {
    }

    public ActivityLogEntry(ActivityType type, String description, String entityType, Long entityId, String actor) {
        this.type = type;
        this.description = description;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actor = actor;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActivityType getType() {
        return type;
    }

    public void setType(ActivityType type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

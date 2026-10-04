package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.domain.repository.ActivityLogRepository;
import io.github.membertracker.infrastructure.persistence.entity.ActivityLogEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ActivityLogDbRepository implements ActivityLogRepository {

    private final ActivityLogJpaRepository activityLogJpaRepository;

    public ActivityLogDbRepository(ActivityLogJpaRepository activityLogJpaRepository) {
        this.activityLogJpaRepository = activityLogJpaRepository;
    }

    @Override
    public ActivityLogEntry save(ActivityLogEntry entry) {
        return mapToDomain(activityLogJpaRepository.save(mapToEntity(entry)));
    }

    @Override
    public List<ActivityLogEntry> findRecent(int limit) {
        return activityLogJpaRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, limit)).stream()
                .map(this::mapToDomain)
                .toList();
    }

    private ActivityLogEntry mapToDomain(ActivityLogEntity entity) {
        ActivityLogEntry entry = new ActivityLogEntry();
        entry.setId(entity.getId());
        entry.setType(ActivityType.valueOf(entity.getActivityType()));
        entry.setDescription(entity.getDescription());
        entry.setEntityType(entity.getEntityType());
        entry.setEntityId(entity.getEntityId());
        entry.setActor(entity.getActor());
        entry.setCreatedAt(entity.getCreatedAt());
        return entry;
    }

    private ActivityLogEntity mapToEntity(ActivityLogEntry entry) {
        ActivityLogEntity entity = new ActivityLogEntity();
        entity.setId(entry.getId());
        entity.setActivityType(entry.getType().name());
        entity.setDescription(entry.getDescription());
        entity.setEntityType(entry.getEntityType());
        entity.setEntityId(entry.getEntityId());
        entity.setActor(entry.getActor());
        entity.setCreatedAt(entry.getCreatedAt());
        return entity;
    }
}

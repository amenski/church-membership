package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.domain.repository.ActivityLogRepository;
import io.github.membertracker.domain.service.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one audit entry. Best effort: a failing audit write is logged at WARN and swallowed, so it
 * never blocks the action being recorded. Descriptions must use names only, never emails or phones.
 */
public class RecordActivityUseCase {

    private static final Logger logger = LoggerFactory.getLogger(RecordActivityUseCase.class);

    private final ActivityLogRepository activityLogRepository;
    private final CurrentActor currentActor;

    public RecordActivityUseCase(ActivityLogRepository activityLogRepository, CurrentActor currentActor) {
        this.activityLogRepository = activityLogRepository;
        this.currentActor = currentActor;
    }

    /** Records the action for the signed-in user. */
    public void record(ActivityType type, String description, String entityType, Long entityId) {
        try {
            save(type, description, entityType, entityId, currentActor.getEmail());
        } catch (Exception e) {
            warn(type, e);
        }
    }

    /** Records the action for a known actor (sign-in: the security context is still empty). */
    public void record(ActivityType type, String description, String entityType, Long entityId,
                       String actorOverride) {
        try {
            save(type, description, entityType, entityId, actorOverride);
        } catch (Exception e) {
            warn(type, e);
        }
    }

    private void save(ActivityType type, String description, String entityType, Long entityId, String actor) {
        activityLogRepository.save(new ActivityLogEntry(type, description, entityType, entityId, actor));
    }

    private static void warn(ActivityType type, Exception e) {
        logger.warn("Could not record activity {}: {}", type, e.getMessage());
    }
}

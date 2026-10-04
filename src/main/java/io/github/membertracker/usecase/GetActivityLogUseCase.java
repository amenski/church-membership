package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.domain.repository.ActivityLogRepository;

import java.util.List;

public class GetActivityLogUseCase {

    private final ActivityLogRepository activityLogRepository;

    public GetActivityLogUseCase(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /** The latest entries, newest first. */
    public List<ActivityLogEntry> invoke(int limit) {
        return activityLogRepository.findRecent(limit);
    }
}

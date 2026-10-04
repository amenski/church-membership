package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.ActivityLogEntry;

import java.util.List;

public interface ActivityLogRepository {
    ActivityLogEntry save(ActivityLogEntry entry);

    /** The latest entries, newest first. */
    List<ActivityLogEntry> findRecent(int limit);
}

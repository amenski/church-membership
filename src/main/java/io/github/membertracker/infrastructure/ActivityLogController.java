package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.usecase.GetActivityLogUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity-log")
@Validated
@Tag(name = "Activity log", description = "Who changed or exported what. Administrators only.")
public class ActivityLogController {

    private final GetActivityLogUseCase getActivityLogUseCase;

    public ActivityLogController(GetActivityLogUseCase getActivityLogUseCase) {
        this.getActivityLogUseCase = getActivityLogUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List the activity log, newest first (ADMIN)")
    public List<ActivityLogEntry> getActivityLog(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return getActivityLogUseCase.invoke(limit);
    }
}

package io.github.membertracker.infrastructure;

import io.github.membertracker.usecase.GetCollectedByMonthUseCase;
import io.github.membertracker.usecase.GetCollectedByMonthUseCase.MonthlyCollected;
import io.github.membertracker.usecase.GetDashboardStatsUseCase;
import io.github.membertracker.usecase.GetDashboardStatsUseCase.DashboardStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Failures are not caught here: they reach {@code GlobalExceptionHandler} and come back as a 500 problem,
 * so an outage is never shown as an empty dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Summary figures for the home screen.")
public class DashboardController {

    private static final int CHART_MONTHS = 12;

    private final GetDashboardStatsUseCase getDashboardStatsUseCase;
    private final GetCollectedByMonthUseCase getCollectedByMonthUseCase;

    @Autowired
    public DashboardController(GetDashboardStatsUseCase getDashboardStatsUseCase,
                               GetCollectedByMonthUseCase getCollectedByMonthUseCase) {
        this.getDashboardStatsUseCase = getDashboardStatsUseCase;
        this.getCollectedByMonthUseCase = getCollectedByMonthUseCase;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Dashboard statistics (VOLUNTEER+)")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(getDashboardStatsUseCase.invoke());
    }

    @GetMapping("/collected-by-month")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Amount collected per billing month for the last 12 months, oldest first (VOLUNTEER+)")
    public ResponseEntity<List<MonthlyCollected>> getCollectedByMonth() {
        return ResponseEntity.ok(getCollectedByMonthUseCase.invoke(CHART_MONTHS));
    }
}

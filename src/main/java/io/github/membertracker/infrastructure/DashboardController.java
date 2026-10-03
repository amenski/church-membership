package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.usecase.GetDashboardStatsUseCase;
import io.github.membertracker.usecase.GetDashboardStatsUseCase.DashboardStats;
import io.github.membertracker.usecase.GetMembersWithMissedPaymentsUseCase;
import io.github.membertracker.usecase.GetRecentCommunicationsUseCase;
import io.github.membertracker.usecase.GetRecentPaymentsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Failures are not caught here: they reach {@code GlobalExceptionHandler} and come back as a 500 problem,
 * so an outage is never shown as an empty dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Summary figures for the home screen.")
public class DashboardController {

    private static final int RECENT_PAYMENTS = 10;
    private static final int ACTIVITIES_PER_SOURCE = 5;
    private static final int ACTIVITIES = 10;

    private final GetDashboardStatsUseCase getDashboardStatsUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    private final GetRecentPaymentsUseCase getRecentPaymentsUseCase;
    private final GetRecentCommunicationsUseCase getRecentCommunicationsUseCase;

    @Autowired
    public DashboardController(GetDashboardStatsUseCase getDashboardStatsUseCase,
                               GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase,
                               GetRecentPaymentsUseCase getRecentPaymentsUseCase,
                               GetRecentCommunicationsUseCase getRecentCommunicationsUseCase) {
        this.getDashboardStatsUseCase = getDashboardStatsUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
        this.getRecentPaymentsUseCase = getRecentPaymentsUseCase;
        this.getRecentCommunicationsUseCase = getRecentCommunicationsUseCase;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Dashboard statistics (VOLUNTEER+)")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(getDashboardStatsUseCase.invoke());
    }

    @GetMapping("/recent-payments")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Ten most recent payments (VOLUNTEER+)")
    public ResponseEntity<List<Payment>> getRecentPayments() {
        return ResponseEntity.ok(getRecentPaymentsUseCase.invoke(RECENT_PAYMENTS));
    }

    @GetMapping("/overdue-members")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Active members overdue by one month or more, longest first (VOLUNTEER+)")
    public ResponseEntity<List<Member>> getOverdueMembers() {
        return ResponseEntity.ok(getMembersWithMissedPaymentsUseCase.invoke(1));
    }

    @GetMapping("/recent-activities")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Recent activity feed (VOLUNTEER+)")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();

        for (Payment p : getRecentPaymentsUseCase.invoke(ACTIVITIES_PER_SOURCE)) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("id", "payment_" + p.getId());
            activity.put("date", p.getPaymentDate());
            activity.put("type", "payment");
            activity.put("description", "Payment received: $" + String.format("%.2f", p.getAmount()));
            activities.add(activity);
        }

        for (Communication c : getRecentCommunicationsUseCase.invoke(ACTIVITIES_PER_SOURCE)) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("id", "comm_" + c.getId());
            activity.put("date", c.getCreatedDate().toLocalDate());
            activity.put("type", "communication");
            activity.put("description", "Communication sent: " + c.getTitle());
            activities.add(activity);
        }

        activities.sort(Comparator.comparing((Map<String, Object> a) -> (LocalDate) a.get("date")).reversed());
        return ResponseEntity.ok(activities.stream().limit(ACTIVITIES).toList());
    }
}

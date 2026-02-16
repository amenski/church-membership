package io.github.membertracker.infrastructure;

import io.github.membertracker.usecase.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    private final GetAllMembersUseCase getAllMembersUseCase;
    private final GetActiveMembersUseCase getActiveMembersUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    private final GetAllPaymentsUseCase getAllPaymentsUseCase;
    private final GetAllCommunicationsUseCase getAllCommunicationsUseCase;

    @Autowired
    public DashboardController(GetAllMembersUseCase getAllMembersUseCase,
                              GetActiveMembersUseCase getActiveMembersUseCase,
                              GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase,
                              GetAllPaymentsUseCase getAllPaymentsUseCase,
                              GetAllCommunicationsUseCase getAllCommunicationsUseCase) {
        this.getAllMembersUseCase = getAllMembersUseCase;
        this.getActiveMembersUseCase = getActiveMembersUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
        this.getAllPaymentsUseCase = getAllPaymentsUseCase;
        this.getAllCommunicationsUseCase = getAllCommunicationsUseCase;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Total members
            List<?> allMembers = getAllMembersUseCase.invoke();
            stats.put("totalMembers", allMembers.size());

            // Active members
            List<?> activeMembers = getActiveMembersUseCase.invoke();
            stats.put("activeMembers", activeMembers.size());

            // Overdue members (members with missed payments)
            List<?> overdueMembers = getMembersWithMissedPaymentsUseCase.invoke(1); // last 1 month
            stats.put("overdueMembers", overdueMembers.size());

            // Monthly revenue (current month)
            YearMonth currentMonth = YearMonth.now();
            List<?> allPayments = getAllPaymentsUseCase.invoke();
            Double monthlyRevenue = allPayments.stream()
                    .filter(payment -> {
                        try {
                            var paymentObj = (io.github.membertracker.domain.model.Payment) payment;
                            return paymentObj.getPeriod() != null && 
                                   paymentObj.getPeriod().equals(currentMonth);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(payment -> {
                        try {
                            return ((io.github.membertracker.domain.model.Payment) payment).getAmount();
                        } catch (Exception e) {
                            return 0.0;
                        }
                    })
                    .reduce(0.0, Double::sum);
            stats.put("monthlyRevenue", monthlyRevenue);

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            // Log the error for debugging
            logger.error("Error retrieving dashboard stats", e);
            // Return default values in case of error
            stats.put("totalMembers", 0);
            stats.put("activeMembers", 0);
            stats.put("overdueMembers", 0);
            stats.put("monthlyRevenue", 0);
            return ResponseEntity.ok(stats);
        }
    }

    @GetMapping("/recent-payments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<?>> getRecentPayments() {
        try {
            List<?> allPayments = getAllPaymentsUseCase.invoke();
            // Return last 10 payments, sorted by payment date descending
            List<?> recentPayments = allPayments.stream()
                    .sorted((p1, p2) -> {
                        try {
                            var payment1 = (io.github.membertracker.domain.model.Payment) p1;
                            var payment2 = (io.github.membertracker.domain.model.Payment) p2;
                            return payment2.getPaymentDate().compareTo(payment1.getPaymentDate());
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .limit(10)
                    .toList();
            return ResponseEntity.ok(recentPayments);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/overdue-members")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<?>> getOverdueMembers() {
        try {
            // Get members with overdue payments (last 1 month)
            List<?> overdueMembers = getMembersWithMissedPaymentsUseCase.invoke(1);
            return ResponseEntity.ok(overdueMembers);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/recent-activities")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivities() {
        try {
            List<Map<String, Object>> activities = new java.util.ArrayList<>();

            // Add recent payments as activities
            List<?> recentPayments = getAllPaymentsUseCase.invoke();
            recentPayments.stream()
                    .sorted((p1, p2) -> {
                        try {
                            var payment1 = (io.github.membertracker.domain.model.Payment) p1;
                            var payment2 = (io.github.membertracker.domain.model.Payment) p2;
                            return payment2.getPaymentDate().compareTo(payment1.getPaymentDate());
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .limit(5)
                    .forEach(payment -> {
                        try {
                            var p = (io.github.membertracker.domain.model.Payment) payment;
                            Map<String, Object> activity = new HashMap<>();
                            activity.put("id", "payment_" + p.getId());
                            activity.put("date", p.getPaymentDate());
                            activity.put("type", "payment");
                            activity.put("description", "Payment received: $" + String.format("%.2f", p.getAmount()));
                            activities.add(activity);
                        } catch (Exception e) {
                            // Skip if casting fails
                        }
                    });

            // Add recent communications as activities
            List<?> communications = getAllCommunicationsUseCase.invoke();
            communications.stream()
                    .sorted((c1, c2) -> {
                        try {
                            var comm1 = (io.github.membertracker.domain.model.Communication) c1;
                            var comm2 = (io.github.membertracker.domain.model.Communication) c2;
                            return comm2.getCreatedDate().compareTo(comm1.getCreatedDate());
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .limit(5)
                    .forEach(comm -> {
                        try {
                            var c = (io.github.membertracker.domain.model.Communication) comm;
                            Map<String, Object> activity = new HashMap<>();
                            activity.put("id", "comm_" + c.getId());
                            activity.put("date", c.getCreatedDate().toLocalDate());
                            activity.put("type", "communication");
                            activity.put("description", "Communication sent: " + c.getTitle());
                            activities.add(activity);
                        } catch (Exception e) {
                            // Skip if casting fails
                        }
                    });

            // Sort all activities by date descending and limit to 10
            activities.sort((a1, a2) -> {
                var date1 = (java.time.temporal.TemporalAccessor) a1.get("date");
                var date2 = (java.time.temporal.TemporalAccessor) a2.get("date");
                return LocalDate.from(date2).compareTo(LocalDate.from(date1));
            });

            return ResponseEntity.ok(activities.stream().limit(10).toList());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }
}
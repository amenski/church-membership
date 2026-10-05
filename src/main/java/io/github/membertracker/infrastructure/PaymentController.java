package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.enumeration.PaymentSortField;
import io.github.membertracker.domain.exception.PaymentDomainException;
import io.github.membertracker.domain.model.PageResult;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.model.PaymentPageQuery;
import io.github.membertracker.infrastructure.dto.RecordPaymentRequest;
import io.github.membertracker.infrastructure.security.ArchivedVisibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.membertracker.usecase.*;
import io.github.membertracker.utils.CsvUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@Validated
@Tag(name = "Payments", description = "Membership dues: record, list and export payments.")
public class PaymentController {

    private static final int MAX_SEARCH_LENGTH = 100;

    private final GetAllPaymentsUseCase getAllPaymentsUseCase;
    private final GetPaymentPageUseCase getPaymentPageUseCase;
    private final GetPaidMonthsUseCase getPaidMonthsUseCase;
    private final GetPaymentByIdUseCase getPaymentByIdUseCase;
    private final GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;
    private final RecordActivityUseCase recordActivityUseCase;

    @Autowired
    public PaymentController(GetAllPaymentsUseCase getAllPaymentsUseCase,
                            GetPaymentPageUseCase getPaymentPageUseCase,
                            GetPaidMonthsUseCase getPaidMonthsUseCase,
                            GetPaymentByIdUseCase getPaymentByIdUseCase,
                            GetPaymentsByMemberUseCase getPaymentsByMemberUseCase,
                            RecordPaymentUseCase recordPaymentUseCase,
                            GetMemberByIdUseCase getMemberByIdUseCase,
                            RecordActivityUseCase recordActivityUseCase) {
        this.getAllPaymentsUseCase = getAllPaymentsUseCase;
        this.getPaymentPageUseCase = getPaymentPageUseCase;
        this.getPaidMonthsUseCase = getPaidMonthsUseCase;
        this.getPaymentByIdUseCase = getPaymentByIdUseCase;
        this.getPaymentsByMemberUseCase = getPaymentsByMemberUseCase;
        this.recordPaymentUseCase = recordPaymentUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
        this.recordActivityUseCase = recordActivityUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List payments (VOLUNTEER+)")
    public List<Payment> getAllPayments() {
        return ArchivedVisibility.redact(getAllPaymentsUseCase.invoke());
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "One page of payments, filtered and sorted (VOLUNTEER+)")
    public PageResult<Payment> getPaymentPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "paymentDate,desc") String sort) {
        PaymentPageQuery query = pageQuery(page, size, search, method, sort);
        PageResult<Payment> result = getPaymentPageUseCase.invoke(query);
        ArchivedVisibility.redact(result.content());
        return result;
    }

    @GetMapping("/paid-months")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "The months each member paid within the last N months (VOLUNTEER+)")
    public Map<Long, List<YearMonth>> getPaidMonths(@RequestParam(defaultValue = "12") @Min(1) @Max(36) int months) {
        return getPaidMonthsUseCase.invoke(months);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a payment by id (VOLUNTEER+)")
    public ResponseEntity<Payment> getPaymentById(@PathVariable @Positive Long id) {
        return getPaymentByIdUseCase.invoke(id)
                .map(payment -> ArchivedVisibility.redact(payment))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List payments of one member (VOLUNTEER+)")
    public ResponseEntity<List<Payment>> getPaymentsByMember(@PathVariable @Positive Long memberId) {
        return ArchivedVisibility.visible(getMemberByIdUseCase.invoke(memberId))
                .map(member -> ResponseEntity.ok(getPaymentsByMemberUseCase.invoke(member)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Record a payment (STAFF+)")
    public ResponseEntity<Payment> recordPayment(@Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.ok(recordPaymentUseCase.invoke(
                request.getMemberId(), request.getAmount(), request.getPaymentMethod(),
                request.getPeriod(), request.getPaymentDate(), request.getNotes()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Export all payments as CSV (STAFF+)")
    public ResponseEntity<byte[]> exportPayments() {
        StringBuilder csv = new StringBuilder("id,memberId,memberName,amount,paymentDate,period,method\n");
        List<Payment> payments = getAllPaymentsUseCase.invoke();
        for (Payment payment : payments) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%s,%.2f,%s,%s,%s%n",
                payment.getId() != null ? payment.getId() : "0",
                payment.getMember() != null && payment.getMember().getId() != null ? payment.getMember().getId() : "0",
                CsvUtils.escapeCsv(payment.getMember() != null ? payment.getMember().getName() : ""),
                payment.getAmount() != null ? payment.getAmount() : 0.0,
                payment.getPaymentDate() != null ? payment.getPaymentDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "",
                payment.getPeriod() != null ? payment.getPeriod().toString() : "",
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : ""));
        }
        recordActivityUseCase.record(ActivityType.PAYMENTS_EXPORTED, "Exported " + payments.size()
                + (payments.size() == 1 ? " payment" : " payments"), "PAYMENT", null);
        return CsvUtils.attachment("payments.csv", csv.toString());
    }

    private static PaymentPageQuery pageQuery(int page, int size, String search, String method, String sort) {
        String trimmed = search == null ? "" : search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw PaymentDomainException.invalidPageQuery("search", "Search must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        PaymentMethod paymentMethod = null;
        if (method != null && !method.isBlank()) {
            try {
                paymentMethod = PaymentMethod.fromCode(method.trim());
            } catch (PaymentDomainException e) {
                throw PaymentDomainException.invalidPageQuery("method", "Unknown payment method");
            }
        }
        String[] parts = sort.split(",", -1);
        PaymentSortField field = PaymentSortField.fromParameter(parts[0].trim()).orElseThrow(() ->
                PaymentDomainException.invalidPageQuery("sort", "Sort by paymentDate, period, amount or member"));
        String direction = parts.length == 1 ? "asc" : parts[1].trim().toLowerCase(Locale.ROOT);
        if (parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
            throw PaymentDomainException.invalidPageQuery("sort", "Sort direction must be asc or desc");
        }
        return new PaymentPageQuery(page, size, trimmed, paymentMethod, field, direction.equals("asc"));
    }
}

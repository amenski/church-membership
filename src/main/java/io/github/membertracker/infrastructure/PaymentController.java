package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.usecase.*;
import io.github.membertracker.utils.CsvUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final GetAllPaymentsUseCase getAllPaymentsUseCase;
    private final GetPaymentByIdUseCase getPaymentByIdUseCase;
    private final GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;

    @Autowired
    public PaymentController(GetAllPaymentsUseCase getAllPaymentsUseCase,
                            GetPaymentByIdUseCase getPaymentByIdUseCase,
                            GetPaymentsByMemberUseCase getPaymentsByMemberUseCase,
                            RecordPaymentUseCase recordPaymentUseCase,
                            GetMemberByIdUseCase getMemberByIdUseCase) {
        this.getAllPaymentsUseCase = getAllPaymentsUseCase;
        this.getPaymentByIdUseCase = getPaymentByIdUseCase;
        this.getPaymentsByMemberUseCase = getPaymentsByMemberUseCase;
        this.recordPaymentUseCase = recordPaymentUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<Payment> getAllPayments() {
        return getAllPaymentsUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {
        return getPaymentByIdUseCase.invoke(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Payment>> getPaymentsByMember(@PathVariable Long memberId) {
        return getMemberByIdUseCase.invoke(memberId)
                .map(member -> ResponseEntity.ok(getPaymentsByMemberUseCase.invoke(member)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Payment> recordPayment(@RequestBody Payment payment) {
        return ResponseEntity.ok(recordPaymentUseCase.invoke(payment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deletePayment(@PathVariable Long id) {
        if (getPaymentByIdUseCase.invoke(id).isPresent()) {
            // In a real application, you might want to revert the member's last payment date
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StreamingResponseBody> exportPayments() {
        List<Payment> payments = getAllPaymentsUseCase.invoke();
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        
        StreamingResponseBody stream = out -> {
            PrintWriter writer = new PrintWriter(out);
            try {
                // CSV Header
                writer.println("id,memberId,memberName,amount,paymentDate,period,method");
                
                // CSV Data
                for (Payment payment : payments) {
                    writer.printf("%s,%s,%s,%.2f,%s,%s,%s%n",
                        payment.getId() != null ? payment.getId() : "0",
                        payment.getMember() != null && payment.getMember().getId() != null ? payment.getMember().getId() : "0",
                        CsvUtils.escapeCsv(payment.getMember() != null ? payment.getMember().getName() : ""),
                        payment.getAmount() != null ? payment.getAmount() : 0.0,
                        payment.getPaymentDate() != null ? payment.getPaymentDate().format(dateFormatter) : "",
                        payment.getPeriod() != null ? payment.getPeriod().toString() : "",
                        payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : ""
                    );
                }
            } catch (Exception e) {
                System.err.println("Error exporting payments: " + e.getMessage());
                throw new RuntimeException("Error exporting payments", e);
            } finally {
                writer.flush();
            }
        };
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(stream);
    }
}

package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.usecase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

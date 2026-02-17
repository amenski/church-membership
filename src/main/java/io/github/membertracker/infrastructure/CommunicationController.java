package io.github.membertracker.infrastructure;


import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.usecase.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/communications")
@Validated
public class CommunicationController {

    private final GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    private final GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    private final CreateCommunicationUseCase createCommunicationUseCase;
    private final SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    private final SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    private final GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase;

    @Autowired
    public CommunicationController(GetAllCommunicationsUseCase getAllCommunicationsUseCase,
                                   GetCommunicationByIdUseCase getCommunicationByIdUseCase,
                                   CreateCommunicationUseCase createCommunicationUseCase,
                                   SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase,
                                   SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase,
                                   GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase,
                                   GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase) {
        this.getAllCommunicationsUseCase = getAllCommunicationsUseCase;
        this.getCommunicationByIdUseCase = getCommunicationByIdUseCase;
        this.createCommunicationUseCase = createCommunicationUseCase;
        this.sendCommunicationToAllMembersUseCase = sendCommunicationToAllMembersUseCase;
        this.sendCommunicationToMembersUseCase = sendCommunicationToMembersUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
        this.getDeliveriesByCommunicationUseCase = getDeliveriesByCommunicationUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<Communication> getAllCommunications() {
        return getAllCommunicationsUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> getCommunicationById(@PathVariable @Positive Long id) {
        return getCommunicationByIdUseCase.invoke(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> createCommunication(@Valid @RequestBody Communication communication) {
        return ResponseEntity.ok(createCommunicationUseCase.invoke(communication));
    }

    @PostMapping("/send-to-all")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> sendToAllMembers(@Valid @RequestBody Communication communication) {
        return ResponseEntity.ok(sendCommunicationToAllMembersUseCase.invoke(communication));
    }

    @PostMapping("/send-to-overdue/{months}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> sendToOverdueMembers(
            @PathVariable @Min(1) int months,
            @Valid @RequestBody Communication communication
    ) {
        List<Member> overdueMembers = getMembersWithMissedPaymentsUseCase.invoke(months);
        return ResponseEntity.ok(
                sendCommunicationToMembersUseCase.invoke(
                        communication,
                        overdueMembers,
                        MessageDelivery.DeliveryChannel.EMAIL
                )
        );
    }

    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<MessageDelivery>> getDeliveries(@PathVariable @Positive Long id) {
        List<MessageDelivery> deliveries = getDeliveriesByCommunicationUseCase.invoke(id);
        return ResponseEntity.ok(deliveries);
    }
}

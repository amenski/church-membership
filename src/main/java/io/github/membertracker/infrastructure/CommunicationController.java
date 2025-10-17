package io.github.membertracker.infrastructure;


import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.usecase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/communications")
public class CommunicationController {

    private final GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    private final GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    private final CreateCommunicationUseCase createCommunicationUseCase;
    private final SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    private final SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    @Autowired
    public CommunicationController(GetAllCommunicationsUseCase getAllCommunicationsUseCase,
                                   GetCommunicationByIdUseCase getCommunicationByIdUseCase,
                                   CreateCommunicationUseCase createCommunicationUseCase,
                                   SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase,
                                   SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase,
                                   GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase) {
        this.getAllCommunicationsUseCase = getAllCommunicationsUseCase;
        this.getCommunicationByIdUseCase = getCommunicationByIdUseCase;
        this.createCommunicationUseCase = createCommunicationUseCase;
        this.sendCommunicationToAllMembersUseCase = sendCommunicationToAllMembersUseCase;
        this.sendCommunicationToMembersUseCase = sendCommunicationToMembersUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<Communication> getAllCommunications() {
        return getAllCommunicationsUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> getCommunicationById(@PathVariable Long id) {
        return getCommunicationByIdUseCase.invoke(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> createCommunication(@RequestBody Communication communication) {
        return ResponseEntity.ok(createCommunicationUseCase.invoke(communication));
    }

    @PostMapping("/send-to-all")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> sendToAllMembers(@RequestBody Communication communication) {
        return ResponseEntity.ok(sendCommunicationToAllMembersUseCase.invoke(communication));
    }

    @PostMapping("/send-to-overdue/{months}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Communication> sendToOverdueMembers(
            @PathVariable int months,
            @RequestBody Communication communication
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
}


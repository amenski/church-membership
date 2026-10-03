package io.github.membertracker.infrastructure;


import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.infrastructure.dto.SendCommunicationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Communications", description = "Emails to members, delivery tracking and retry.")
public class CommunicationController {

    private final GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    private final GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;
    private final CreateCommunicationUseCase createCommunicationUseCase;
    private final SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    private final SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    private final GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase;
    private final RetryDeliveryUseCase retryDeliveryUseCase;

    @Autowired
    public CommunicationController(GetAllCommunicationsUseCase getAllCommunicationsUseCase,
                                   GetCommunicationByIdUseCase getCommunicationByIdUseCase,
                                   GetMemberByIdUseCase getMemberByIdUseCase,
                                   CreateCommunicationUseCase createCommunicationUseCase,
                                   SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase,
                                   SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase,
                                   GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase,
                                   GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase,
                                   RetryDeliveryUseCase retryDeliveryUseCase) {
        this.getAllCommunicationsUseCase = getAllCommunicationsUseCase;
        this.getCommunicationByIdUseCase = getCommunicationByIdUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
        this.createCommunicationUseCase = createCommunicationUseCase;
        this.sendCommunicationToAllMembersUseCase = sendCommunicationToAllMembersUseCase;
        this.sendCommunicationToMembersUseCase = sendCommunicationToMembersUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
        this.getDeliveriesByCommunicationUseCase = getDeliveriesByCommunicationUseCase;
        this.retryDeliveryUseCase = retryDeliveryUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List communications (VOLUNTEER+)")
    public List<Communication> getAllCommunications() {
        return getAllCommunicationsUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a communication by id (VOLUNTEER+)")
    public ResponseEntity<Communication> getCommunicationById(@PathVariable @Positive Long id) {
        return getCommunicationByIdUseCase.invoke(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a communication without sending it (STAFF+)")
    public ResponseEntity<Communication> createCommunication(@Valid @RequestBody SendCommunicationRequest request) {
        return ResponseEntity.ok(createCommunicationUseCase.invoke(toCommunication(request)));
    }

    @PostMapping("/send-to-all")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Send a communication to all members (STAFF+)")
    public ResponseEntity<Communication> sendToAllMembers(@Valid @RequestBody SendCommunicationRequest request) {
        return ResponseEntity.ok(sendCommunicationToAllMembersUseCase.invoke(toCommunication(request)));
    }

    @PostMapping("/send-to-overdue/{months}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Send a communication by email to members overdue by the given months (STAFF+)")
    public ResponseEntity<Communication> sendToOverdueMembers(
            @PathVariable @Min(1) int months,
            @Valid @RequestBody SendCommunicationRequest request
    ) {
        List<Member> overdueMembers = getMembersWithMissedPaymentsUseCase.invoke(months);
        return ResponseEntity.ok(
                sendCommunicationToMembersUseCase.invoke(
                        toCommunication(request),
                        overdueMembers,
                        MessageDelivery.DeliveryChannel.EMAIL
                )
        );
    }

    @PostMapping("/send-to-member/{memberId}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Send a communication by email to one member (STAFF+)")
    public ResponseEntity<Communication> sendToMember(
            @PathVariable @Positive Long memberId,
            @Valid @RequestBody SendCommunicationRequest request
    ) {
        Member member = getMemberByIdUseCase.invoke(memberId)
                .orElseThrow(() -> MemberDomainException.memberNotFound(memberId));
        return ResponseEntity.ok(
                sendCommunicationToMembersUseCase.invoke(
                        toCommunication(request),
                        List.of(member),
                        MessageDelivery.DeliveryChannel.EMAIL
                )
        );
    }

    /** Builds a fresh Communication from the request; nothing else comes from the client. */
    private Communication toCommunication(SendCommunicationRequest request) {
        Communication communication = new Communication();
        communication.setTitle(request.getTitle());
        communication.setMessageContent(request.getMessageContent());
        communication.setType(request.getType() != null ? request.getType() : CommunicationType.ANNOUNCEMENT);
        return communication;
    }

    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List delivery attempts of a communication (VOLUNTEER+)")
    public ResponseEntity<List<MessageDelivery>> getDeliveries(@PathVariable @Positive Long id) {
        List<MessageDelivery> deliveries = getDeliveriesByCommunicationUseCase.invoke(id);
        return ResponseEntity.ok(deliveries);
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/retry")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Retry a failed delivery (STAFF+)")
    public ResponseEntity<MessageDelivery> retryDelivery(@PathVariable @Positive Long id,
                                                         @PathVariable @Positive Long deliveryId) {
        return ResponseEntity.ok(retryDeliveryUseCase.invoke(id, deliveryId));
    }
}

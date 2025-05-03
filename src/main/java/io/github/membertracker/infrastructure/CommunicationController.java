package io.github.membertracker.infrastructure;


import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.service.CommunicationService;
import io.github.membertracker.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    private final CommunicationService communicationService;
    private final MemberService memberService;

    @Autowired
    public CommunicationController(CommunicationService communicationService,
                                   MemberService memberService) {
        this.communicationService = communicationService;
        this.memberService = memberService;
    }

    @GetMapping
    public List<Communication> getAllCommunications() {
        return communicationService.getAllCommunications();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Communication> getCommunicationById(@PathVariable Long id) {
        return communicationService.getCommunicationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Communication> createCommunication(@RequestBody Communication communication) {
        return ResponseEntity.ok(communicationService.createCommunication(communication));
    }

    @PostMapping("/send-to-all")
    public ResponseEntity<Communication> sendToAllMembers(@RequestBody Communication communication) {
        return ResponseEntity.ok(communicationService.sendToAllMembers(communication));
    }

    @PostMapping("/send-to-overdue/{months}")
    public ResponseEntity<Communication> sendToOverdueMembers(
            @PathVariable int months,
            @RequestBody Communication communication
    ) {
        List<Member> overdueMembers = memberService.getMembersWithMissedPayments(months);
        return ResponseEntity.ok(
                communicationService.sendToMembers(
                        communication,
                        overdueMembers,
                        MessageDelivery.DeliveryChannel.EMAIL
                )
        );
    }
}


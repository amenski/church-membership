package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.usecase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final GetAllMembersUseCase getAllMembersUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;
    private final GetActiveMembersUseCase getActiveMembersUseCase;
    private final GetInactiveMembersUseCase getInactiveMembersUseCase;
    private final SaveMemberUseCase saveMemberUseCase;
    private final DeleteMemberUseCase deleteMemberUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    @Autowired
    public MemberController(GetAllMembersUseCase getAllMembersUseCase,
                           GetMemberByIdUseCase getMemberByIdUseCase,
                           GetActiveMembersUseCase getActiveMembersUseCase,
                           GetInactiveMembersUseCase getInactiveMembersUseCase,
                           SaveMemberUseCase saveMemberUseCase,
                           DeleteMemberUseCase deleteMemberUseCase,
                           GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase) {
        this.getAllMembersUseCase = getAllMembersUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
        this.getActiveMembersUseCase = getActiveMembersUseCase;
        this.getInactiveMembersUseCase = getInactiveMembersUseCase;
        this.saveMemberUseCase = saveMemberUseCase;
        this.deleteMemberUseCase = deleteMemberUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<Member> getAllMembers() {
        return getAllMembersUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Member> getMemberById(@PathVariable Long id) {
        return getMemberByIdUseCase.invoke(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('USER')")
    public List<Member> getActiveMembers() {
        return getActiveMembersUseCase.invoke();
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasRole('USER')")
    public List<Member> getInactiveMembers() {
        return getInactiveMembersUseCase.invoke();
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Member createMember(@RequestBody Member member) {
        return saveMemberUseCase.invoke(member);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Member> updateMember(@PathVariable Long id, @RequestBody Member member) {
        return getMemberByIdUseCase.invoke(id)
                .map(existingMember -> {
                    member.setId(id);
                    return ResponseEntity.ok(saveMemberUseCase.invoke(member));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        if (getMemberByIdUseCase.invoke(id).isPresent()) {
            deleteMemberUseCase.invoke(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/overdue/{months}")
    public List<Member> getMembersWithOverduePayments(@PathVariable int months) {
        return getMembersWithMissedPaymentsUseCase.invoke(months);
    }
}

package io.github.membertracker.usecase;

import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Edits the client-managed fields of a stored member. The missed-month counters and the last
 * payment date are system-managed and never touched here.
 */
public class UpdateMemberUseCase {

    private final MemberRepository memberRepository;

    public UpdateMemberUseCase(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Optional<Member> invoke(Long id, String name, String email, String phone,
                                   LocalDate joinDate, Boolean active) {
        return memberRepository.findById(id).map(member -> {
            memberRepository.findByEmailIgnoreCase(email)
                    .filter(other -> !other.getId().equals(member.getId()))
                    .ifPresent(other -> {
                        throw MemberDomainException.emailAlreadyExists(email);
                    });

            member.setName(name);
            member.setEmail(email);
            member.setPhone(phone);
            if (joinDate != null) {
                member.setJoinDate(joinDate);
            }
            if (active != null) {
                if (active && !member.isActive()) {
                    member.activate();
                } else if (!active && member.isActive()) {
                    member.deactivate();
                }
            }
            return memberRepository.save(member);
        });
    }
}

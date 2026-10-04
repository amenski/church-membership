package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.domain.repository.PaymentRepository;

import java.util.Optional;

/**
 * Removes a member row for good, only for a record made by mistake: refused when the member has any payment or
 * any message delivery. The database enforces the same rule (ON DELETE RESTRICT, migration 011).
 */
public class DeleteMemberPermanentlyUseCase {

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final MessageDeliveryRepository messageDeliveryRepository;
    private final RecordActivityUseCase recordActivity;

    public DeleteMemberPermanentlyUseCase(MemberRepository memberRepository, PaymentRepository paymentRepository,
                                          MessageDeliveryRepository messageDeliveryRepository,
                                          RecordActivityUseCase recordActivity) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.messageDeliveryRepository = messageDeliveryRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @return true when the member was deleted, false when there is no member with that id
     * @throws MemberDomainException (409) when the member has payments or message deliveries
     */
    public boolean invoke(Long id) {
        Optional<Member> found = memberRepository.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        Member member = found.get();
        long payments = paymentRepository.countByMemberId(id);
        long deliveries = messageDeliveryRepository.countByRecipientId(id);
        if (payments > 0 || deliveries > 0) {
            throw MemberDomainException.memberHasHistory(member.getName(), payments, deliveries);
        }
        memberRepository.deleteById(id);
        recordActivity.record(ActivityType.MEMBER_DELETED, "Member " + member.getName() + " was deleted permanently",
                "MEMBER", id);
        return true;
    }
}

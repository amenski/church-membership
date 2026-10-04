package io.github.membertracker.infrastructure.persistence.mapper;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;

import java.time.YearMonth;

/**
 * The one place a {@link MemberEntity} and a {@link Member} are converted into each other.
 */
public final class MemberPersistenceMapper {

    private MemberPersistenceMapper() {
    }

    public static Member toDomain(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        member.setName(entity.getName());
        member.setEmail(entity.getEmail());
        member.setPhone(entity.getPhone());
        member.setJoinDate(entity.getJoinDate());
        member.setLastPaymentDate(entity.getLastPaymentDate());
        member.setConsecutiveMonthsMissed(entity.getConsecutiveMonthsMissed());
        member.setLastMissedCountMonth(entity.getLastMissedCountMonth() == null
                ? null : YearMonth.parse(entity.getLastMissedCountMonth()));
        member.setActive(entity.isActive());
        return member;
    }

    public static MemberEntity toEntity(Member member) {
        MemberEntity entity = new MemberEntity();
        entity.setId(member.getId());
        entity.setName(member.getName());
        entity.setEmail(member.getEmail());
        entity.setPhone(member.getPhone());
        entity.setJoinDate(member.getJoinDate());
        entity.setLastPaymentDate(member.getLastPaymentDate());
        entity.setConsecutiveMonthsMissed(member.getConsecutiveMonthsMissed());
        entity.setLastMissedCountMonth(member.getLastMissedCountMonth() == null
                ? null : member.getLastMissedCountMonth().toString());
        entity.setActive(member.isActive());
        return entity;
    }

    /**
     * The short form kept on a message delivery: id, name, email, phone and active only,
     * so the delivery JSON carries no join date or counters.
     */
    public static Member toRecipient(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        member.setName(entity.getName());
        member.setEmail(entity.getEmail());
        member.setPhone(entity.getPhone());
        member.setActive(entity.isActive());
        return member;
    }
}

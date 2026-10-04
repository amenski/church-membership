package io.github.membertracker.infrastructure.persistence.mapper;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.persistence.entity.HouseholdEntity;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PersonEntity;

import java.time.YearMonth;

/**
 * The one place a {@link MemberEntity} and a {@link Member} are converted into each other.
 * The domain reads the status and ignores the legacy {@code active} column; the entity gets both, written here.
 * Name, email and phone are read from the linked {@link PersonEntity} (plan step 9); the legacy member columns are
 * only written, until step 12. The household (id and name) is read from the person too; the delivery summary
 * ({@link #toRecipient}) leaves it out.
 */
public final class MemberPersistenceMapper {

    private MemberPersistenceMapper() {
    }

    public static Member toDomain(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        copyFromPerson(entity.getPerson(), member);
        member.setJoinDate(entity.getJoinDate());
        member.setLastPaymentDate(entity.getLastPaymentDate());
        member.setConsecutiveMonthsMissed(entity.getConsecutiveMonthsMissed());
        member.setLastMissedCountMonth(entity.getLastMissedCountMonth() == null
                ? null : YearMonth.parse(entity.getLastMissedCountMonth()));
        member.setStatus(MemberStatus.valueOf(entity.getStatus()));
        member.setArchivedAt(entity.getArchivedAt());
        copyHouseholdFromPerson(entity.getPerson(), member);
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
        entity.setStatus(member.getStatus().name());
        entity.setArchivedAt(member.getArchivedAt());
        // The only writer of the legacy column: it follows the status, so the two never disagree.
        entity.setActive(member.getStatus().countsForDues());
        // A transient person carrying the same three values, so an entity built here reads back like the member
        // (payment and delivery references). MemberDbRepository.save swaps in the stored person before it saves.
        PersonEntity person = new PersonEntity();
        copyToPerson(member, person);
        if (member.getHouseholdId() != null) {
            // A reference with the id and the name only, never saved: MemberDbRepository.save resolves the real household.
            HouseholdEntity household = new HouseholdEntity();
            household.setId(member.getHouseholdId());
            household.setName(member.getHouseholdName());
            person.setHousehold(household);
        }
        entity.setPerson(person);
        return entity;
    }

    private static void copyFromPerson(PersonEntity person, Member member) {
        member.setName(person.getName());
        member.setEmail(person.getEmail());
        member.setPhone(person.getPhone());
    }

    private static void copyHouseholdFromPerson(PersonEntity person, Member member) {
        HouseholdEntity household = person.getHousehold();
        member.setHouseholdId(household == null ? null : household.getId());
        member.setHouseholdName(household == null ? null : household.getName());
    }

    /**
     * DUAL-WRITE, remove at plan step 12: copies the three fields that live in both tables onto the person row, so
     * the legacy member columns and the person never disagree. The person is the one read back (step 9). MemberDbRepository.save is the only caller.
     */
    public static void copyToPerson(Member member, PersonEntity person) {
        person.setName(member.getName());
        person.setEmail(member.getEmail());
        person.setPhone(member.getPhone());
    }

    /**
     * DUAL-WRITE, remove at plan step 12: the other direction of {@link #copyToPerson}. A person edited on its own
     * (PersonDbRepository.save) rewrites the legacy columns of its membership from the person's values.
     */
    public static void copyToLegacyColumns(PersonEntity person, MemberEntity member) {
        member.setName(person.getName());
        member.setEmail(person.getEmail());
        member.setPhone(person.getPhone());
    }

    /**
     * The short form kept on a message delivery: id, name, email, phone and status (so {@code active}) only,
     * so the delivery JSON carries no join date or counters.
     */
    public static Member toRecipient(MemberEntity entity) {
        Member member = new Member();
        member.setId(entity.getId());
        copyFromPerson(entity.getPerson(), member);
        member.setStatus(MemberStatus.valueOf(entity.getStatus()));
        return member;
    }
}

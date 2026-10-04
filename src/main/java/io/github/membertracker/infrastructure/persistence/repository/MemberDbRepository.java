package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.HouseholdEntity;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PersonEntity;
import io.github.membertracker.infrastructure.persistence.mapper.MemberPersistenceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class MemberDbRepository implements MemberRepository {

    private final MemberJpaRepository memberJpaRepository;
    private final HouseholdJpaRepository householdJpaRepository;
    private final PersonJpaRepository personJpaRepository;

    public MemberDbRepository(MemberJpaRepository memberJpaRepository, HouseholdJpaRepository householdJpaRepository,
                              PersonJpaRepository personJpaRepository) {
        this.memberJpaRepository = memberJpaRepository;
        this.householdJpaRepository = householdJpaRepository;
        this.personJpaRepository = personJpaRepository;
    }

    private static final String MEMBER = MemberStatus.MEMBER.name();
    private static final String ARCHIVED = MemberStatus.ARCHIVED.name();

    @Override
    public List<Member> findAll() {
        return toDomain(memberJpaRepository.findByStatusNotOrderByIdAsc(ARCHIVED));
    }

    @Override
    public Optional<Member> findById(Long id) {
        return memberJpaRepository.findById(id)
                .map(MemberPersistenceMapper::toDomain);
    }

    @Override
    public List<Member> findByStatus(MemberStatus status) {
        return toDomain(memberJpaRepository.findByStatusOrderByIdAsc(status.name()));
    }

    @Override
    public List<Member> findDuesPaying() {
        return findByStatus(MemberStatus.MEMBER);
    }

    @Override
    public List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months) {
        return toDomain(memberJpaRepository.findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByIdAsc(MEMBER, months));
    }

    @Override
    public long countNotArchived() {
        return memberJpaRepository.countByStatusNot(ARCHIVED);
    }

    @Override
    public long countDuesPaying() {
        return memberJpaRepository.countByStatus(MEMBER);
    }

    @Override
    public long countDuesPayingWithMissedAtLeast(int months) {
        return memberJpaRepository.countByStatusAndConsecutiveMonthsMissedGreaterThanEqual(MEMBER, months);
    }

    @Override
    public List<Member> findDuesPayingWithMissedAtLeastOrderByMissedDesc(int months) {
        return toDomain(memberJpaRepository
                .findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescPersonNameAscIdAsc(MEMBER, months));
    }

    /**
     * Name, email and phone go to the linked person row. A new member creates its person; an edit updates the person
     * it already has. One transaction, so a member never exists without its person.
     */
    @Override
    @Transactional
    public Member save(Member member) {
        MemberEntity entity = MemberPersistenceMapper.toEntity(member);
        PersonEntity person = member.getId() == null ? null : memberJpaRepository.findById(member.getId())
                .map(MemberEntity::getPerson).orElse(null);
        boolean isStored = person != null;
        if (!isStored) {
            person = entity.getPerson();
        }
        MemberPersistenceMapper.writeToPerson(member, person);
        person.setHousehold(resolveHousehold(member.getHouseholdId(), isStored ? person.getHousehold() : null));
        entity.setPerson(person);
        return MemberPersistenceMapper.toDomain(memberJpaRepository.save(entity));
    }

    /** A new membership for a stored person: same entity as {@link #save}, linked to that person's row (step 11). */
    @Override
    @Transactional
    public Member saveForPerson(Long personId, Member member) {
        PersonEntity person = personJpaRepository.findById(personId)
                .orElseThrow(() -> new IllegalStateException("Person not found"));
        MemberEntity entity = MemberPersistenceMapper.toEntity(member);
        entity.setPerson(person);
        return MemberPersistenceMapper.toDomain(memberJpaRepository.save(entity));
    }

    /**
     * The household a save points the person at: the stored one when it is unchanged (no lookup), else the household
     * with that id. A member request naming a household that does not exist is refused here (400, HOUSEHOLD_001),
     * because this is where the reference is resolved.
     */
    private HouseholdEntity resolveHousehold(Long wanted, HouseholdEntity current) {
        if (wanted == null) {
            return null;
        }
        if (current != null && wanted.equals(current.getId())) {
            return current;
        }
        return householdJpaRepository.findById(wanted).orElseThrow(HouseholdDomainException::notFound);
    }

    @Override
    public void deleteById(Long id) {
        memberJpaRepository.deleteById(id);
    }

    private static List<Member> toDomain(List<MemberEntity> entities) {
        return entities.stream()
                .map(MemberPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }
}
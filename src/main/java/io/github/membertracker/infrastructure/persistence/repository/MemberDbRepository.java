package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
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

    public MemberDbRepository(MemberJpaRepository memberJpaRepository) {
        this.memberJpaRepository = memberJpaRepository;
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
     * DUAL-WRITE, remove at plan step 12: name, email and phone go to the legacy member columns (toEntity) and to the
     * linked person row. A new member creates its person; an edit updates the person it already has. Reads use the
     * person only (step 9), so saving also heals a legacy column that drifted. One transaction, so a member never exists without its person.
     */
    @Override
    @Transactional
    public Member save(Member member) {
        MemberEntity entity = MemberPersistenceMapper.toEntity(member);
        PersonEntity person = member.getId() == null ? null : memberJpaRepository.findById(member.getId())
                .map(MemberEntity::getPerson).orElse(null);
        if (person == null) {
            person = entity.getPerson();
        }
        MemberPersistenceMapper.copyToPerson(member, person);
        entity.setPerson(person);
        return MemberPersistenceMapper.toDomain(memberJpaRepository.save(entity));
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
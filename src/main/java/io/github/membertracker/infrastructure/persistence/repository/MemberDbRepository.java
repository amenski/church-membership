package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.mapper.MemberPersistenceMapper;
import org.springframework.stereotype.Repository;

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
                .findByStatusAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescNameAscIdAsc(MEMBER, months));
    }

    @Override
    public Member save(Member member) {
        MemberEntity entity = MemberPersistenceMapper.toEntity(member);
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
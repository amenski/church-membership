package io.github.membertracker.infrastructure.persistence.repository;

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

    @Override
    public List<Member> findAll() {
        return memberJpaRepository.findAll().stream()
                .map(MemberPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Member> findById(Long id) {
        return memberJpaRepository.findById(id)
                .map(MemberPersistenceMapper::toDomain);
    }

    @Override
    public List<Member> findByActive(boolean active) {
        return memberJpaRepository.findByActive(active).stream()
                .map(MemberPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months) {
        return memberJpaRepository.findByConsecutiveMonthsMissedGreaterThanEqual(months).stream()
                .map(MemberPersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countAll() {
        return memberJpaRepository.count();
    }

    @Override
    public long countByActive(boolean active) {
        return memberJpaRepository.countByActive(active);
    }

    @Override
    public long countActiveWithMissedAtLeast(int months) {
        return memberJpaRepository.countByActiveTrueAndConsecutiveMonthsMissedGreaterThanEqual(months);
    }

    @Override
    public List<Member> findActiveWithMissedAtLeastOrderByMissedDesc(int months) {
        return memberJpaRepository
                .findByActiveTrueAndConsecutiveMonthsMissedGreaterThanEqualOrderByConsecutiveMonthsMissedDescNameAscIdAsc(months)
                .stream()
                .map(MemberPersistenceMapper::toDomain)
                .collect(Collectors.toList());
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
}
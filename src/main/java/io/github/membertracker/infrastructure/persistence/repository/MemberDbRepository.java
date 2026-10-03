package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
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
                .map(this::mapToMember)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Member> findById(Long id) {
        return memberJpaRepository.findById(id)
                .map(this::mapToMember);
    }

    @Override
    public List<Member> findByActive(boolean active) {
        return memberJpaRepository.findByActive(active).stream()
                .map(this::mapToMember)
                .collect(Collectors.toList());
    }

    @Override
    public List<Member> findByConsecutiveMonthsMissedGreaterThanEqual(int months) {
        return memberJpaRepository.findByConsecutiveMonthsMissedGreaterThanEqual(months).stream()
                .map(this::mapToMember)
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
                .map(this::mapToMember)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return memberJpaRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public Optional<Member> findByEmailIgnoreCase(String email) {
        return memberJpaRepository.findByEmailIgnoreCase(email)
                .map(this::mapToMember);
    }

    @Override
    public Member save(Member member) {
        MemberEntity entity = mapToEntity(member);
        return mapToMember(memberJpaRepository.save(entity));
    }

    @Override
    public void deleteById(Long id) {
        memberJpaRepository.deleteById(id);
    }

    private Member mapToMember(MemberEntity entity) {
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

    private MemberEntity mapToEntity(Member member) {
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
}
package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
    public List<Member> findMembersWithLastPaymentBefore(LocalDate date) {
        return memberJpaRepository.findMembersWithLastPaymentBefore(date).stream()
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
        entity.setActive(member.isActive());
        return entity;
    }
}
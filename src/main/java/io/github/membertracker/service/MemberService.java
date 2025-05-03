package io.github.membertracker.service;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    @Autowired
    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public Optional<Member> getMemberById(Long id) {
        return memberRepository.findById(id);
    }

    public List<Member> getActiveMembers() {
        return memberRepository.findByActive(true);
    }

    public List<Member> getInactiveMembers() {
        return memberRepository.findByActive(false);
    }

    public Member saveMember(Member member) {
        return memberRepository.save(member);
    }

    public void deleteMember(Long id) {
        memberRepository.deleteById(id);
    }

    public List<Member> getMembersWithMissedPayments(int monthsThreshold) {
        return memberRepository.findByConsecutiveMonthsMissedGreaterThanEqual(monthsThreshold);
    }

    public List<Member> getMembersWithoutRecentPayment(int monthsBack) {
        LocalDate cutoffDate = LocalDate.now().minusMonths(monthsBack);
        return memberRepository.findMembersWithLastPaymentBefore(cutoffDate);
    }
}

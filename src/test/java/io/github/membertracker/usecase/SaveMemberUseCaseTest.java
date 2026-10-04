package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SaveMemberUseCaseTest {

    private MemberRepository memberRepository;
    private RecordActivityUseCase recordActivity;
    private SaveMemberUseCase useCase;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        recordActivity = mock(RecordActivityUseCase.class);
        useCase = new SaveMemberUseCase(memberRepository, recordActivity);
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> {
            Member m = i.getArgument(0);
            m.setId(9L);
            return m;
        });
    }

    private Member saved() {
        ArgumentCaptor<Member> c = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void newMemberWithoutJoinDateGetsTodayAndIsActiveWithZeroCounters() {
        Member result = useCase.invoke("Dan", "dan@example.com", null, null);

        Member s = saved();
        assertThat(result).isSameAs(s);
        assertThat(s.getName()).isEqualTo("Dan");
        assertThat(s.getEmail()).isEqualTo("dan@example.com");
        assertThat(s.getPhone()).isNull();
        assertThat(s.getJoinDate()).isEqualTo(LocalDate.now());
        assertThat(s.isActive()).isTrue();
        assertThat(s.getConsecutiveMonthsMissed()).isZero();
        assertThat(s.getLastPaymentDate()).isNull();
        assertThat(s.getLastMissedCountMonth()).isNull();
    }

    @Test
    void creatingRecordsTheNameButNeitherEmailNorPhone() {
        useCase.invoke("Dan Smith", "dan@example.com", "+390612345678", null);

        verify(recordActivity).record(ActivityType.MEMBER_CREATED, "Member Dan Smith was added", "MEMBER", 9L);
    }

    @Test
    void newMemberKeepsExplicitJoinDate() {
        LocalDate earlier = LocalDate.now().minusMonths(2);

        useCase.invoke("Dan", "dan@example.com", "+390612345678", earlier);

        assertThat(saved().getJoinDate()).isEqualTo(earlier);
        assertThat(saved().getPhone()).isEqualTo("+390612345678");
    }

    @Test
    void duplicateEmailIsRejectedAndNothingIsSaved() {
        when(memberRepository.existsByEmailIgnoreCase("DAN@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.invoke("Dan", "DAN@Example.com", null, null))
            .isInstanceOf(MemberDomainException.class)
            .hasMessage("A member with this email already exists");

        verify(memberRepository, never()).save(any());
        verifyNoInteractions(recordActivity);
    }
}

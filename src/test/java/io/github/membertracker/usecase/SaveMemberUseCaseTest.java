package io.github.membertracker.usecase;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveMemberUseCaseTest {

    private MemberRepository memberRepository;
    private SaveMemberUseCase useCase;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        useCase = new SaveMemberUseCase(memberRepository);
        when(memberRepository.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Member saved() {
        ArgumentCaptor<Member> c = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void newMemberWithoutJoinDateGetsTodayAndIsActivated() {
        Member m = new Member();
        m.setName("Dan");
        m.setActive(false);

        Member result = useCase.invoke(m);

        assertThat(result).isSameAs(m);
        assertThat(saved().getJoinDate()).isEqualTo(LocalDate.now());
        assertThat(saved().isActive()).isTrue();
    }

    @Test
    void newMemberKeepsExplicitJoinDate() {
        Member m = new Member();
        LocalDate earlier = LocalDate.now().minusMonths(2);
        m.setJoinDate(earlier);

        useCase.invoke(m);

        assertThat(saved().getJoinDate()).isEqualTo(earlier);
    }

    @Test
    void existingMemberIsSavedUnchangedEvenWhenInactiveWithoutJoinDate() {
        Member m = new Member();
        m.setId(7L);
        m.setActive(false);

        useCase.invoke(m);

        Member s = saved();
        assertThat(s.isActive()).isFalse();
        assertThat(s.getJoinDate()).isNull();
    }
}

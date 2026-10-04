package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.exception.MemberDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteMemberPermanentlyUseCaseTest {

    private final MemberRepository repo = mock(MemberRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final MessageDeliveryRepository deliveries = mock(MessageDeliveryRepository.class);
    private final RecordActivityUseCase recordActivity = mock(RecordActivityUseCase.class);
    private final DeleteMemberPermanentlyUseCase useCase =
        new DeleteMemberPermanentlyUseCase(repo, payments, deliveries, recordActivity);

    private void stored() {
        when(repo.findById(42L)).thenReturn(Optional.of(new Member("Jane Smith", null, null)));
    }

    @Test
    void refusesWithAConflictWhenTheMemberHasPayments() {
        stored();
        when(payments.countByMemberId(42L)).thenReturn(2L);

        assertThatThrownBy(() -> useCase.invoke(42L))
            .isInstanceOfSatisfying(MemberDomainException.class, e -> {
                assertThat(e.isConflict()).isTrue();
                assertThat(e.getErrorCode()).isEqualTo(MemberDomainException.MEMBER_HAS_HISTORY);
                assertThat(e.getMessage()).contains("2 payment(s)");
            });
        verify(repo, never()).deleteById(anyLong());
        verifyNoInteractions(recordActivity);
    }

    @Test
    void refusesWithAConflictWhenTheMemberHasDeliveries() {
        stored();
        when(deliveries.countByRecipientId(42L)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.invoke(42L))
            .isInstanceOfSatisfying(MemberDomainException.class, e -> assertThat(e.isConflict()).isTrue());
        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void deletesAMemberWithoutHistoryAndRecordsIt() {
        stored();

        assertThat(useCase.invoke(42L)).isTrue();

        verify(repo).deleteById(42L);
        verify(recordActivity).record(ActivityType.MEMBER_DELETED, "Member Jane Smith was deleted permanently", "MEMBER", 42L);
    }

    @Test
    void anUnknownIdIsReportedAndDeletesNothing() {
        when(repo.findById(42L)).thenReturn(Optional.empty());

        assertThat(useCase.invoke(42L)).isFalse();

        verify(repo, never()).deleteById(anyLong());
        verifyNoInteractions(recordActivity);
    }
}

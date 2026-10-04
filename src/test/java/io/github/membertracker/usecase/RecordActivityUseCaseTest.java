package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.domain.repository.ActivityLogRepository;
import io.github.membertracker.domain.service.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordActivityUseCaseTest {

    private ActivityLogRepository repository;
    private CurrentActor currentActor;
    private RecordActivityUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ActivityLogRepository.class);
        currentActor = mock(CurrentActor.class);
        when(currentActor.getEmail()).thenReturn("staff@example.com");
        useCase = new RecordActivityUseCase(repository, currentActor);
    }

    private ActivityLogEntry saved() {
        ArgumentCaptor<ActivityLogEntry> c = ArgumentCaptor.forClass(ActivityLogEntry.class);
        verify(repository).save(c.capture());
        return c.getValue();
    }

    @Test
    void savesTheEntryWithTheActorFromThePort() {
        LocalDateTime before = LocalDateTime.now();

        useCase.record(ActivityType.MEMBER_CREATED, "Member Jane Smith was added", "MEMBER", 5L);

        ActivityLogEntry entry = saved();
        assertThat(entry.getType()).isEqualTo(ActivityType.MEMBER_CREATED);
        assertThat(entry.getDescription()).isEqualTo("Member Jane Smith was added");
        assertThat(entry.getEntityType()).isEqualTo("MEMBER");
        assertThat(entry.getEntityId()).isEqualTo(5L);
        assertThat(entry.getActor()).isEqualTo("staff@example.com");
        assertThat(entry.getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void anExplicitActorWinsOverThePort() {
        useCase.record(ActivityType.SIGN_IN, "Signed in", "USER", null, "admin@example.com");

        assertThat(saved().getActor()).isEqualTo("admin@example.com");
    }

    @Test
    void aFailingRepositoryIsSwallowed() {
        when(repository.save(any())).thenThrow(new IllegalStateException("database is down"));

        assertThatCode(() -> useCase.record(ActivityType.MEMBER_DELETED, "x", "MEMBER", 1L)).doesNotThrowAnyException();
        assertThatCode(() -> useCase.record(ActivityType.SIGN_IN, "x", "USER", null, "a@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void aFailingActorLookupIsSwallowedToo() {
        when(currentActor.getEmail()).thenThrow(new IllegalStateException("no context"));

        assertThatCode(() -> useCase.record(ActivityType.MEMBER_DELETED, "x", "MEMBER", 1L)).doesNotThrowAnyException();
    }
}

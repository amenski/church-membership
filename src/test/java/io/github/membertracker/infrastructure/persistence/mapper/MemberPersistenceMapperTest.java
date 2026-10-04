package io.github.membertracker.infrastructure.persistence.mapper;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.PersonEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPersistenceMapperTest {

    private Member fullMember() {
        Member member = new Member();
        member.setId(7L);
        member.setName("Abebe Kebede");
        member.setEmail("abebe@example.com");
        member.setPhone("+251911234567");
        member.setJoinDate(LocalDate.of(2024, 1, 15));
        member.setLastPaymentDate(LocalDate.of(2026, 8, 3));
        member.setConsecutiveMonthsMissed(2);
        member.setLastMissedCountMonth(YearMonth.of(2026, 9));
        member.setStatus(MemberStatus.INACTIVE);
        member.setArchivedAt(LocalDateTime.of(2026, 9, 30, 10, 15));
        return member;
    }

    @Test
    void everyFieldSurvivesARoundTrip() {
        Member original = fullMember();

        Member back = MemberPersistenceMapper.toDomain(MemberPersistenceMapper.toEntity(original));

        assertThat(back).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void entityHoldsTheMarkerAsYearMonthText() {
        MemberEntity entity = MemberPersistenceMapper.toEntity(fullMember());

        assertThat(entity.getLastMissedCountMonth()).isEqualTo("2026-09");
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void aNullEmailAndANullMarkerStayNull() {
        Member original = fullMember();
        original.setEmail(null);
        original.setLastMissedCountMonth(null);

        MemberEntity entity = MemberPersistenceMapper.toEntity(original);
        Member back = MemberPersistenceMapper.toDomain(entity);

        assertThat(entity.getEmail()).isNull();
        assertThat(entity.getLastMissedCountMonth()).isNull();
        assertThat(back.getEmail()).isNull();
        assertThat(back.getLastMissedCountMonth()).isNull();
    }

    @Test
    void recipientSummaryCarriesOnlyIdNameEmailPhoneAndActive() {
        MemberEntity entity = MemberPersistenceMapper.toEntity(fullMember());

        Member recipient = MemberPersistenceMapper.toRecipient(entity);

        assertThat(recipient.getId()).isEqualTo(7L);
        assertThat(recipient.getName()).isEqualTo("Abebe Kebede");
        assertThat(recipient.getEmail()).isEqualTo("abebe@example.com");
        assertThat(recipient.getPhone()).isEqualTo("+251911234567");
        assertThat(recipient.isActive()).isFalse();
        assertThat(recipient.getJoinDate()).isNull();
        assertThat(recipient.getLastPaymentDate()).isNull();
        assertThat(recipient.getConsecutiveMonthsMissed()).isZero();
        assertThat(recipient.getLastMissedCountMonth()).isNull();
    }

    @Test
    void activeIsWrittenAsTheStatusImpliesForAllFiveStatusesAndTheTwoNeverDisagree() {
        for (MemberStatus status : MemberStatus.values()) {
            Member member = fullMember();
            member.setStatus(status);

            MemberEntity entity = MemberPersistenceMapper.toEntity(member);

            assertThat(entity.getStatus()).as(status.name()).isEqualTo(status.name());
            assertThat(entity.isActive()).as(status.name()).isEqualTo(status.countsForDues());
            Member back = MemberPersistenceMapper.toDomain(entity);
            assertThat(back.getStatus()).as(status.name()).isEqualTo(status);
            assertThat(back.isActive()).as(status.name()).isEqualTo(entity.isActive());
            assertThat(MemberPersistenceMapper.toRecipient(entity).isActive()).as(status.name()).isEqualTo(status.countsForDues());
        }
    }

    @Test
    void theStatusColumnWinsWhenTheLegacyActiveColumnIsStale() {
        MemberEntity entity = MemberPersistenceMapper.toEntity(fullMember());
        entity.setStatus("DECEASED");
        entity.setActive(true);

        Member member = MemberPersistenceMapper.toDomain(entity);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DECEASED);
        assertThat(member.isActive()).isFalse();
    }

    @Test
    void copyToPersonWritesNameEmailAndPhoneIncludingNulls() {
        PersonEntity person = new PersonEntity();
        Member member = fullMember();

        MemberPersistenceMapper.copyToPerson(member, person);
        assertThat(person.getName()).isEqualTo("Abebe Kebede");
        assertThat(person.getEmail()).isEqualTo("abebe@example.com");
        assertThat(person.getPhone()).isEqualTo("+251911234567");

        member.setEmail(null);
        member.setPhone(null);
        MemberPersistenceMapper.copyToPerson(member, person);
        assertThat(person.getEmail()).isNull();
        assertThat(person.getPhone()).isNull();
    }
}

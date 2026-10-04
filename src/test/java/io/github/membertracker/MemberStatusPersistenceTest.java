package io.github.membertracker;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Through the real repository and database: the status is stored, and the legacy active column follows it. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:memberstatus;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class MemberStatusPersistenceTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;

    @AfterEach
    void cleanUp() {
        memberJpaRepository.deleteAll();
    }

    @Test
    void everyStatusIsStoredAndActiveIsKeptInStep() {
        for (MemberStatus status : MemberStatus.values()) {
            Member member = new Member("Status " + status, null, null);
            member.setStatus(status);

            Member saved = memberRepository.save(member);

            MemberEntity row = memberJpaRepository.findById(saved.getId()).orElseThrow();
            assertThat(row.getStatus()).as(status.name()).isEqualTo(status.name());
            assertThat(row.isActive()).as(status.name()).isEqualTo(status.countsForDues());
            assertThat(memberRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(status);
        }
    }

    @Test
    void theStatusQueriesSeeTheRightMembersAndHideTheArchived() {
        Member deceased = new Member("Gone", null, null);
        deceased.setStatus(MemberStatus.DECEASED);
        memberRepository.save(deceased);
        memberRepository.save(new Member("Here", null, null));

        Member archived = new Member("Hidden", null, null);
        archived.setStatus(MemberStatus.ARCHIVED);
        memberRepository.save(archived);

        assertThat(memberRepository.findDuesPaying()).extracting(Member::getName).containsExactly("Here");
        assertThat(memberRepository.findByStatus(MemberStatus.DECEASED)).extracting(Member::getName).containsExactly("Gone");
        assertThat(memberRepository.findByStatus(MemberStatus.ARCHIVED)).extracting(Member::getName).containsExactly("Hidden");
        assertThat(memberRepository.findAll()).extracting(Member::getName).containsExactly("Gone", "Here");
        assertThat(memberRepository.countDuesPaying()).isEqualTo(1);
        assertThat(memberRepository.countNotArchived()).isEqualTo(2);
    }

    @Test
    void changingTheStatusMovesTheLegacyColumnToo() {
        Member saved = memberRepository.save(new Member("Moving", null, null));

        saved.setStatus(MemberStatus.TRANSFERRED);
        memberRepository.save(saved);
        assertThat(memberJpaRepository.findById(saved.getId()).orElseThrow().isActive()).isFalse();

        saved.activate();
        memberRepository.save(saved);
        assertThat(memberJpaRepository.findById(saved.getId()).orElseThrow().isActive()).isTrue();
    }
}

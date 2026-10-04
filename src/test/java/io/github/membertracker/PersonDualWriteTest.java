package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.usecase.ArchiveMemberUseCase;
import io.github.membertracker.usecase.DeleteMemberPermanentlyUseCase;
import io.github.membertracker.usecase.SaveMemberUseCase;
import io.github.membertracker.usecase.UpdateMemberUseCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Step 8 dual-write, through the real use cases, repository and database: every create and edit writes name, email and
 * phone to the legacy member columns and to the linked person row, and the drift query stays empty throughout.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:persondualwrite;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class PersonDualWriteTest {

    @Autowired private SaveMemberUseCase saveMember;
    @Autowired private UpdateMemberUseCase updateMember;
    @Autowired private ArchiveMemberUseCase archiveMember;
    @Autowired private DeleteMemberPermanentlyUseCase deleteMemberPermanently;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        memberJpaRepository.deleteAll();
    }

    private List<Map<String, Object>> drift() {
        return jdbc.queryForList(PersonDriftQuery.SQL);
    }

    private Map<String, Object> person(Long memberId) {
        return jdbc.queryForMap("SELECT p.* FROM person p JOIN member m ON m.person_id = p.id WHERE m.id = ?", memberId);
    }

    @Test
    void creatingAMemberCreatesItsPersonWithTheSameValues() {
        Member saved = saveMember.invoke("Abebe Kebede", "abebe@example.org", "+390611", null, null);

        Map<String, Object> person = person(saved.getId());
        assertThat(person).containsEntry("NAME", "Abebe Kebede").containsEntry("EMAIL", "abebe@example.org")
            .containsEntry("PHONE", "+390611");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class)).isEqualTo(1);
        assertThat(drift()).isEmpty();
    }

    @Test
    void aMemberWithoutEmailOrPhoneGetsAPersonWithNulls() {
        Member saved = saveMember.invoke("Child", null, null, null, MemberStatus.INACTIVE);

        assertThat(person(saved.getId())).containsEntry("EMAIL", null).containsEntry("PHONE", null);
        assertThat(drift()).isEmpty();
    }

    @Test
    void twoMembersMayShareAnEmailAndEachKeepsItsOwnPerson() {
        Member mother = saveMember.invoke("Mother", "family@example.org", null, null, null);
        Member father = saveMember.invoke("Father", "family@example.org", null, null, null);

        assertThat(person(mother.getId()).get("ID")).isNotEqualTo(person(father.getId()).get("ID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE email = 'family@example.org'", Integer.class))
            .isEqualTo(2);
        assertThat(drift()).isEmpty();
    }

    @Test
    void editingNameEmailAndPhoneChangesBothRowsAndAddsNoPerson() {
        Member saved = saveMember.invoke("Old Name", "old@example.org", "+390611", null, null);

        updateMember.invoke(saved.getId(), "New Name", "new@example.org", "+390622", null, null, null).orElseThrow();

        Map<String, Object> member = jdbc.queryForMap("SELECT name, email, phone FROM member WHERE id = ?", saved.getId());
        assertThat(member).containsEntry("NAME", "New Name").containsEntry("EMAIL", "new@example.org").containsEntry("PHONE", "+390622");
        assertThat(person(saved.getId())).containsEntry("NAME", "New Name").containsEntry("EMAIL", "new@example.org")
            .containsEntry("PHONE", "+390622");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class)).isEqualTo(1);
        assertThat(drift()).isEmpty();
    }

    @Test
    void clearingTheEmailAndPhoneStoresNullInBothPlaces() {
        Member saved = saveMember.invoke("Clearing", "gone@example.org", "+390611", null, null);

        updateMember.invoke(saved.getId(), "Clearing", null, null, null, null, null).orElseThrow();

        assertThat(jdbc.queryForMap("SELECT email, phone FROM member WHERE id = ?", saved.getId()))
            .containsEntry("EMAIL", null).containsEntry("PHONE", null);
        assertThat(person(saved.getId())).containsEntry("EMAIL", null).containsEntry("PHONE", null);
        assertThat(drift()).isEmpty();
    }

    @Test
    void archiveAndRestoreKeepThePersonInStepWithoutChangingIt() {
        Member saved = saveMember.invoke("Archivable", "a@example.org", "+390611", null, null);
        Object personId = person(saved.getId()).get("ID");

        archiveMember.invoke(saved.getId()).orElseThrow();
        assertThat(memberRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.ARCHIVED);
        assertThat(person(saved.getId()).get("ID")).isEqualTo(personId);
        assertThat(drift()).isEmpty();

        updateMember.invoke(saved.getId(), "Archivable", "a@example.org", "+390611", null, MemberStatus.INACTIVE, null)
            .orElseThrow();
        assertThat(person(saved.getId()).get("ID")).isEqualTo(personId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class)).isEqualTo(1);
        assertThat(drift()).isEmpty();
    }

    @Test
    void anEditWhileArchivedStillUpdatesThePerson() {
        Member saved = saveMember.invoke("Before", "b@example.org", null, null, null);
        archiveMember.invoke(saved.getId()).orElseThrow();

        updateMember.invoke(saved.getId(), "After", "c@example.org", "+390633", null, MemberStatus.INACTIVE, null).orElseThrow();

        assertThat(person(saved.getId())).containsEntry("NAME", "After").containsEntry("EMAIL", "c@example.org");
        assertThat(drift()).isEmpty();
    }

    @Test
    void permanentlyDeletingAMemberRemovesItsPersonToo() {
        Member keep = saveMember.invoke("Keep", "k@example.org", null, null, null);
        Member mistake = saveMember.invoke("Mistake", "m@example.org", null, null, null);

        assertThat(deleteMemberPermanently.invoke(mistake.getId())).isTrue();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person", Integer.class)).isEqualTo(1);
        assertThat(person(keep.getId())).containsEntry("NAME", "Keep");
        assertThat(drift()).isEmpty();
    }

    @Test
    void savingWithOnlyCounterChangesLeavesPersonUntouched() {
        Member saved = saveMember.invoke("Counter", "c@example.org", "+390611", null, null);
        saved.setConsecutiveMonthsMissed(3);

        memberRepository.save(saved);

        assertThat(person(saved.getId())).containsEntry("NAME", "Counter");
        assertThat(drift()).isEmpty();
    }
}

package io.github.membertracker;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.ActivityLogEntry;
import io.github.membertracker.domain.repository.ActivityLogRepository;
import io.github.membertracker.infrastructure.persistence.entity.ActivityLogEntity;
import io.github.membertracker.infrastructure.persistence.repository.ActivityLogJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit trail through the real stack on in-memory H2: a staff member adds a member and exports,
 * an administrator reads the log. EmailService is mocked; nothing here sends mail.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:activitylog;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class ActivityLogIntegrationTest {

    private static final String STAFF = "staff@example.com";
    private static final String MEMBER_JSON =
        "{\"name\":\"Abel Tesfaye\",\"email\":\"abel@example.com\",\"phone\":\"+390612345678\",\"joinDate\":\"2025-01-01\",\"active\":true}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ActivityLogJpaRepository activityLogJpaRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;

    @MockitoBean private EmailService emailService;
    @MockitoSpyBean private ActivityLogRepository activityLogRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        activityLogJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private void asStaffAddMemberAndExport() throws Exception {
        mockMvc.perform(post("/api/members").with(user(STAFF).roles("STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MEMBER_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/export").with(user(STAFF).roles("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void addingAMemberAndExportingAreRecordedWithTheStaffEmailAsActor() throws Exception {
        asStaffAddMemberAndExport();

        List<ActivityLogEntity> rows = activityLogJpaRepository.findAll();
        assertThat(rows).extracting(ActivityLogEntity::getActivityType)
                .containsExactlyInAnyOrder("MEMBER_CREATED", "MEMBERS_EXPORTED");
        assertThat(rows).extracting(ActivityLogEntity::getActor).containsOnly(STAFF);
        assertThat(rows).extracting(ActivityLogEntity::getDescription)
                .containsExactlyInAnyOrder("Member Abel Tesfaye was added", "Exported 1 member");
        assertThat(rows).extracting(ActivityLogEntity::getDescription)
                .noneMatch(d -> d.contains("@") || d.contains("+39"));
    }

    @Test
    void anAdministratorReadsTheLogNewestFirst() throws Exception {
        asStaffAddMemberAndExport();

        String body = mockMvc.perform(get("/api/activity-log").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$[*].type")).containsExactly("MEMBERS_EXPORTED", "MEMBER_CREATED");
        assertThat(JsonPath.<List<String>>read(body, "$[*].actor")).containsOnly(STAFF);
        assertThat((String) JsonPath.read(body, "$[1].description")).isEqualTo("Member Abel Tesfaye was added");
        assertThat((String) JsonPath.read(body, "$[1].entityType")).isEqualTo("MEMBER");
        assertThat((Object) JsonPath.read(body, "$[1].entityId")).isNotNull();
        assertThat((String) JsonPath.read(body, "$[0].createdAt")).isNotBlank();
        assertThat((Object) JsonPath.read(body, "$[0].id")).isNotNull();
    }

    @Test
    void limitCapsTheNumberOfEntries() throws Exception {
        asStaffAddMemberAndExport();

        mockMvc.perform(get("/api/activity-log?limit=1").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("MEMBERS_EXPORTED"));
    }

    @Test
    void staffCannotReadTheLog() throws Exception {
        mockMvc.perform(get("/api/activity-log").with(user(STAFF).roles("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aLimitOutsideOneToTwoHundredIsA400WithAFieldError() throws Exception {
        for (String limit : List.of("0", "500")) {
            mockMvc.perform(get("/api/activity-log?limit=" + limit).with(user("admin@example.com").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("limit"))
                    .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        }
    }

    @Test
    void aFailingAuditWriteDoesNotFailAddingTheMember() throws Exception {
        doThrow(new IllegalStateException("audit table is gone")).when(activityLogRepository).save(any());

        mockMvc.perform(post("/api/members").with(user(STAFF).roles("STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MEMBER_JSON))
                .andExpect(status().isOk());

        assertThat(memberJpaRepository.count()).isEqualTo(1);
        assertThat(activityLogJpaRepository.count()).isZero();
    }

    @Test
    void everyTypeTheSampleDataWroteIsStillReadable() throws Exception {
        for (String legacy : List.of("SYSTEM_STARTUP", "BULK_IMPORT", "PAYMENT_REMINDER_SENT", "MEMBER_DEACTIVATED")) {
            ActivityLogEntity row = new ActivityLogEntity();
            row.setActivityType(legacy);
            row.setDescription("From the sample data");
            row.setCreatedAt(java.time.LocalDateTime.of(2023, 1, 1, 0, 0));
            activityLogJpaRepository.save(row);
        }

        String body = mockMvc.perform(get("/api/activity-log").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$[*].type"))
                .containsExactlyInAnyOrder("SYSTEM_STARTUP", "BULK_IMPORT", "PAYMENT_REMINDER_SENT", "MEMBER_DEACTIVATED");
    }

    @Test
    void theEnumNameIsWhatIsStored() {
        activityLogRepository.save(new ActivityLogEntry(
                ActivityType.PASSWORD_CHANGED, "Password was changed", "USER", 3L, "a@example.com"));

        assertThat(activityLogJpaRepository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getActivityType()).isEqualTo("PASSWORD_CHANGED"));
    }
}

package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.HouseholdJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import io.github.membertracker.usecase.ArchiveMemberUseCase;
import io.github.membertracker.usecase.SaveMemberUseCase;
import jakarta.persistence.EntityManagerFactory;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Step 10 through the real controllers, use cases, repositories and an in-memory H2 database: create a household,
 * assign and unassign people through the member API, the delete rule, who sees archived members, the activity log,
 * and the drift query staying empty. Real persistence, no mocks.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:householdflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true"
    })
@AutoConfigureMockMvc
class HouseholdFlowIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.org").roles("ADMIN");
    private static final RequestPostProcessor STAFF = user("staff@example.org").roles("STAFF");
    private static final RequestPostProcessor VOLUNTEER = user("vol@example.org").roles("VOLUNTEER");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private SaveMemberUseCase saveMember;
    @Autowired private ArchiveMemberUseCase archiveMember;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private HouseholdJpaRepository householdJpaRepository;

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
        householdJpaRepository.deleteAll();
        jdbc.update("DELETE FROM activity_log");
    }

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                        RequestPostProcessor role, int expectedStatus) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(role))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
    }

    private long createHousehold(String name) throws Exception {
        String body = send(post("/api/households").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"city\":\"Roma\",\"notes\":\"gate code 1234\"}"), STAFF, 200);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void putMember(Member member, String extraJson, int expectedStatus) throws Exception {
        send(put("/api/members/" + member.getId()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + member.getName() + "\"" + extraJson + "}"), STAFF, expectedStatus);
    }

    private String member(long id) throws Exception {
        return send(get("/api/members/" + id), VOLUNTEER, 200);
    }

    private List<Map<String, Object>> drift() {
        return jdbc.queryForList(PersonDriftQuery.SQL);
    }

    private List<String> activityTypes() {
        return jdbc.queryForList("SELECT activity_type FROM activity_log ORDER BY id", String.class);
    }

    @Test
    void createAssignUnassignAndDeleteFlow() throws Exception {
        long householdId = createHousehold("Kebede family");
        Member abebe = saveMember.invoke("Abebe Kebede", null, null, null, null);
        Member tigist = saveMember.invoke("Tigist Kebede", null, null, null, null);

        // a new household is empty and can be read back whole
        send(get("/api/households/" + householdId), VOLUNTEER, 200);
        assertThat(JsonPath.<Integer>read(send(get("/api/households"), VOLUNTEER, 200), "$[0].memberCount")).isZero();

        // assign through the member request: the member JSON carries id and name
        putMember(abebe, ",\"householdId\":" + householdId, 200);
        putMember(tigist, ",\"householdId\":" + householdId, 200);
        String assigned = member(abebe.getId());
        assertThat(JsonPath.<Integer>read(assigned, "$.householdId")).isEqualTo((int) householdId);
        assertThat(JsonPath.<String>read(assigned, "$.householdName")).isEqualTo("Kebede family");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE household_id = ?", Integer.class, householdId)).isEqualTo(2);

        // the detail lists the people with id, name and status; the list counts them
        String detail = send(get("/api/households/" + householdId), VOLUNTEER, 200);
        assertThat(JsonPath.<List<String>>read(detail, "$.members[*].name")).containsExactly("Abebe Kebede", "Tigist Kebede");
        assertThat(JsonPath.<List<String>>read(detail, "$.members[*].status")).containsOnly("MEMBER");
        assertThat(JsonPath.<String>read(detail, "$.notes")).isEqualTo("gate code 1234");
        String list = send(get("/api/households"), VOLUNTEER, 200);
        assertThat(JsonPath.<Integer>read(list, "$[0].memberCount")).isEqualTo(2);
        assertThat(JsonPath.<String>read(list, "$[0].city")).isEqualTo("Roma");

        // the member list carries the household too
        assertThat(JsonPath.<List<String>>read(send(get("/api/members"), VOLUNTEER, 200), "$[*].householdName"))
            .containsExactly("Kebede family", "Kebede family");

        // an edit that does not mention the household keeps it (the "mark inactive" request)
        putMember(abebe, ",\"status\":\"INACTIVE\"", 200);
        assertThat(JsonPath.<Integer>read(member(abebe.getId()), "$.householdId")).isEqualTo((int) householdId);
        assertThat(JsonPath.<String>read(member(abebe.getId()), "$.status")).isEqualTo("INACTIVE");

        // a household with people cannot be deleted, and nobody is changed by the attempt
        String refused = send(delete("/api/households/" + householdId), ADMIN, 409);
        assertThat(JsonPath.<String>read(refused, "$.code")).isEqualTo("HOUSEHOLD_002");
        assertThat(refused).doesNotContain("Kebede");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE household_id = ?", Integer.class, householdId)).isEqualTo(2);

        // unassign with an explicit null, then the delete goes through and the people stay
        putMember(abebe, ",\"householdId\":null", 200);
        putMember(tigist, ",\"householdId\":null", 200);
        assertThat(member(abebe.getId())).contains("\"householdId\":null").contains("\"householdName\":null");
        assertThat(JsonPath.<List<?>>read(send(get("/api/households/" + householdId), VOLUNTEER, 200), "$.members")).isEmpty();
        send(delete("/api/households/" + householdId), ADMIN, 200);
        send(get("/api/households/" + householdId), VOLUNTEER, 404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household", Integer.class)).isZero();

        // the audit trail: no addresses, notes or contact data in it
        assertThat(activityTypes()).contains("HOUSEHOLD_CREATED", "MEMBER_HOUSEHOLD_CHANGED", "HOUSEHOLD_DELETED");
        assertThat(jdbc.queryForList("SELECT description FROM activity_log", String.class))
            .noneMatch(d -> d.contains("gate code") || d.contains("Roma"));
        assertThat(drift()).isEmpty();
    }

    @Test
    void aMemberCanBeCreatedStraightIntoAHouseholdAndMovedBetweenHouseholds() throws Exception {
        long first = createHousehold("First family");
        long second = createHousehold("Second family");

        String created = send(post("/api/members").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Newcomer\",\"householdId\":" + first + "}"), STAFF, 200);
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        assertThat(JsonPath.<String>read(created, "$.householdName")).isEqualTo("First family");

        Member newcomer = new Member();
        newcomer.setId(id);
        newcomer.setName("Newcomer");
        putMember(newcomer, ",\"householdId\":" + second, 200);

        assertThat(JsonPath.<String>read(member(id), "$.householdName")).isEqualTo("Second family");
        String list = send(get("/api/households"), VOLUNTEER, 200);
        assertThat(JsonPath.<List<String>>read(list, "$[*].name")).containsExactly("First family", "Second family");
        assertThat(JsonPath.<List<Integer>>read(list, "$[*].memberCount")).containsExactly(0, 1);
        assertThat(drift()).isEmpty();
    }

    @Test
    void anUnknownHouseholdIsRefusedWith400AndTheCode() throws Exception {
        Member abebe = saveMember.invoke("Abebe Kebede", null, null, null, null);

        mockMvc.perform(put("/api/members/" + abebe.getId()).with(csrf()).with(STAFF).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Abebe Kebede\",\"householdId\":987654}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("HOUSEHOLD_001"))
            .andExpect(jsonPath("$.errors[0].field").value("householdId"));
        mockMvc.perform(post("/api/members").with(csrf()).with(STAFF).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nobody\",\"householdId\":987654}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("HOUSEHOLD_001"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member", Integer.class)).isEqualTo(1);
        assertThat(JsonPath.<Object>read(member(abebe.getId()), "$.householdId")).isNull();
    }

    @Test
    void archivedMembersStayInTheHouseholdButOnlyAnAdminSeesThem() throws Exception {
        long householdId = createHousehold("Kebede family");
        Member abebe = saveMember.invoke("Abebe Kebede", null, null, null, null);
        Member tigist = saveMember.invoke("Tigist Kebede", null, null, null, null);
        putMember(abebe, ",\"householdId\":" + householdId, 200);
        putMember(tigist, ",\"householdId\":" + householdId, 200);
        archiveMember.invoke(tigist.getId()).orElseThrow();

        String staff = send(get("/api/households/" + householdId), STAFF, 200);
        assertThat(JsonPath.<List<String>>read(staff, "$.members[*].name")).containsExactly("Abebe Kebede");
        assertThat(staff).doesNotContain("Tigist");
        assertThat(JsonPath.<Integer>read(send(get("/api/households"), VOLUNTEER, 200), "$[0].memberCount")).isEqualTo(1);

        String admin = send(get("/api/households/" + householdId), ADMIN, 200);
        assertThat(JsonPath.<List<String>>read(admin, "$.members[*].name")).containsExactly("Abebe Kebede", "Tigist Kebede");
        assertThat(JsonPath.<List<String>>read(admin, "$.members[*].status")).containsExactly("MEMBER", "ARCHIVED");
        assertThat(JsonPath.<Integer>read(send(get("/api/households"), ADMIN, 200), "$[0].memberCount")).isEqualTo(2);

        // an archived person still counts as a person: the household cannot be deleted from under them
        putMember(abebe, ",\"householdId\":null", 200);
        send(delete("/api/households/" + householdId), ADMIN, 409);
        assertThat(drift()).isEmpty();
    }

    @Test
    void updatingAHouseholdChangesItsFieldsAndShowsOnItsMembers() throws Exception {
        long householdId = createHousehold("Old name");
        Member abebe = saveMember.invoke("Abebe Kebede", null, null, null, null);
        putMember(abebe, ",\"householdId\":" + householdId, 200);

        String updated = send(put("/api/households/" + householdId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"New name\",\"addressLine1\":\"Via Milano 2\"}"), STAFF, 200);

        assertThat(JsonPath.<String>read(updated, "$.name")).isEqualTo("New name");
        assertThat(JsonPath.<String>read(updated, "$.addressLine1")).isEqualTo("Via Milano 2");
        assertThat(JsonPath.<Object>read(updated, "$.city")).isNull();
        assertThat(JsonPath.<List<String>>read(updated, "$.members[*].name")).containsExactly("Abebe Kebede");
        assertThat(JsonPath.<String>read(member(abebe.getId()), "$.householdName")).isEqualTo("New name");
        send(put("/api/households/987654").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"), STAFF, 404);
        assertThat(activityTypes()).contains("HOUSEHOLD_CREATED", "HOUSEHOLD_UPDATED");
    }

    @Test
    void aSavedPaymentAndTheListsAreStillOneJoinedStatementWithHouseholds() throws Exception {
        long householdId = createHousehold("Kebede family");
        Member abebe = saveMember.invoke("Abebe Kebede", null, null, null, null);
        Member tigist = saveMember.invoke("Tigist Kebede", null, null, null, null);
        putMember(abebe, ",\"householdId\":" + householdId, 200);
        putMember(tigist, ",\"householdId\":" + householdId, 200);
        Member stored = memberJpaRepository.findById(abebe.getId()).map(io.github.membertracker.infrastructure.persistence.mapper.MemberPersistenceMapper::toDomain).orElseThrow();
        paymentRepository.save(new Payment(stored, YearMonth.now(), 25.0, PaymentMethod.CASH));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        String members = send(get("/api/members"), VOLUNTEER, 200);
        assertThat(statistics.getPrepareStatementCount()).as("member list").isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(members, "$[*].householdName")).containsOnly("Kebede family");

        statistics.clear();
        assertThat(paymentRepository.findAll()).hasSize(1).allSatisfy(
            payment -> assertThat(payment.getMember().getHouseholdName()).isEqualTo("Kebede family"));
        assertThat(statistics.getPrepareStatementCount()).as("payment list").isEqualTo(1);
        String payments = send(get("/api/payments"), VOLUNTEER, 200);
        assertThat(JsonPath.<String>read(payments, "$[0].member.householdName")).isEqualTo("Kebede family");

        statistics.clear();
        send(get("/api/households"), VOLUNTEER, 200);
        assertThat(statistics.getPrepareStatementCount()).as("household list").isEqualTo(1);
        statistics.clear();
        send(get("/api/households/" + householdId), VOLUNTEER, 200);
        assertThat(statistics.getPrepareStatementCount()).as("household detail").isLessThanOrEqualTo(2);
    }
}

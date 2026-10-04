package io.github.membertracker;

import com.jayway.jsonpath.JsonPath;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.infrastructure.persistence.repository.MemberJpaRepository;
import io.github.membertracker.infrastructure.persistence.repository.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Archive, list, restore and permanent delete through the real endpoints, use cases and repositories on H2. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:memberarchive;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class MemberArchiveIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;

    private Member paid;
    private Member fresh;

    private static RequestPostProcessor admin() {
        return user("admin@example.com").roles("ADMIN");
    }

    private static RequestPostProcessor staff() {
        return user("staff@example.com").roles("STAFF");
    }

    @BeforeEach
    void create() {
        paid = memberRepository.save(new Member("Paid Pat", "pat@example.com", "+390612345678"));
        fresh = memberRepository.save(new Member("Fresh Fay", "fay@example.com", "+390612345679"));
        paymentRepository.save(new Payment(paid, YearMonth.now(), 25.0, PaymentMethod.CASH));
    }

    @AfterEach
    void cleanUp() {
        paymentJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private List<String> names(String url, RequestPostProcessor who) throws Exception {
        String body = mockMvc.perform(get(url).with(who)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].name");
    }

    private String putStatus(Member member, String status) {
        return "{\"name\":\"" + member.getName() + "\",\"email\":\"" + member.getEmail() + "\",\"status\":\"" + status + "\"}";
    }

    @Test
    void archiveHidesTheMemberFromTheListKeepsPaymentsAndShowsThemInTheArchivedList() throws Exception {
        mockMvc.perform(delete("/api/members/" + paid.getId()).with(csrf()).with(admin())).andExpect(status().isOk());

        assertThat(names("/api/members", admin())).containsExactly("Fresh Fay");
        assertThat(names("/api/members/inactive", staff())).isEmpty();
        assertThat(names("/api/members?archived=true", admin())).containsExactly("Paid Pat");
        Member row = memberRepository.findById(paid.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(MemberStatus.ARCHIVED);
        assertThat(row.getArchivedAt()).isNotNull();
        assertThat(paymentRepository.countByMemberId(paid.getId())).isEqualTo(1);
    }

    @Test
    void theArchivedListIsForAdministratorsOnly() throws Exception {
        mockMvc.perform(get("/api/members?archived=true").with(staff())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/members?archived=true").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void anArchivedMemberIsNotFoundByIdForStaffButFoundForAdmin() throws Exception {
        mockMvc.perform(delete("/api/members/" + paid.getId()).with(csrf()).with(admin())).andExpect(status().isOk());

        mockMvc.perform(get("/api/members/" + paid.getId()).with(staff())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/members/" + paid.getId()).with(admin())).andExpect(status().isOk());
        mockMvc.perform(put("/api/members/" + paid.getId()).with(csrf()).with(staff())
                .contentType(MediaType.APPLICATION_JSON).content(putStatus(paid, "MEMBER")))
            .andExpect(status().isNotFound());
        assertThat(memberRepository.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(MemberStatus.ARCHIVED);
    }

    @Test
    void restoringBringsTheMemberBackToTheList() throws Exception {
        mockMvc.perform(delete("/api/members/" + paid.getId()).with(csrf()).with(admin())).andExpect(status().isOk());

        mockMvc.perform(put("/api/members/" + paid.getId()).with(csrf()).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(putStatus(paid, "MEMBER")))
            .andExpect(status().isOk());

        assertThat(names("/api/members", staff())).containsExactly("Paid Pat", "Fresh Fay");
        assertThat(names("/api/members?archived=true", admin())).isEmpty();
        assertThat(memberRepository.findById(paid.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    void permanentDeleteIsRefusedWithAConflictWhenThereAreAPaymentAndAllowedWithout() throws Exception {
        mockMvc.perform(delete("/api/members/" + paid.getId() + "/permanent").with(csrf()).with(admin()))
            .andExpect(status().isConflict());
        assertThat(memberRepository.findById(paid.getId())).isPresent();

        mockMvc.perform(delete("/api/members/" + fresh.getId() + "/permanent").with(csrf()).with(admin()))
            .andExpect(status().isOk());
        assertThat(memberRepository.findById(fresh.getId())).isEmpty();
        mockMvc.perform(delete("/api/members/" + fresh.getId() + "/permanent").with(csrf()).with(admin()))
            .andExpect(status().isNotFound());
    }
}

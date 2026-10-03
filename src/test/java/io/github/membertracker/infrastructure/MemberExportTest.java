package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class MemberExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetAllMembersUseCase getAllMembersUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetActiveMembersUseCase getActiveMembersUseCase;
    @MockitoBean private GetInactiveMembersUseCase getInactiveMembersUseCase;
    @MockitoBean private SaveMemberUseCase saveMemberUseCase;
    @MockitoBean private UpdateMemberUseCase updateMemberUseCase;
    @MockitoBean private DeleteMemberUseCase deleteMemberUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    private static Member member(long id, String name) {
        Member member = new Member(name, "m" + id + "@example.com", "+390612345678");
        member.setId(id);
        member.setJoinDate(LocalDate.of(2024, 1, 15));
        return member;
    }

    private static final String BOM = "\uFEFF";

    private MockHttpServletResponse export(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(csrf()).with(user("v@example.com").roles("VOLUNTEER")))
            .andReturn();
        assertThat(result.getRequest().isAsyncStarted()).isFalse();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse();
    }

    private static String body(MockHttpServletResponse response) throws Exception {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void exportsOnlyTheRequestedMembersWithFormulaGuard() throws Exception {
        when(getAllMembersUseCase.invoke()).thenReturn(List.of(
            member(1, "Abel"), member(2, "=HYPERLINK(\"x\")"), member(3, "Selam")));

        MockHttpServletResponse response = export(post("/api/members/export")
            .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1,2]}"));
        String csv = body(response);
        assertThat(csv).startsWith(BOM);
        String[] lines = csv.substring(BOM.length()).strip().split("\\R");

        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=members.csv");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("id,name,email,phone,joinDate,active,consecutiveMonthsMissed");
        assertThat(lines[1]).startsWith("1,Abel,");
        assertThat(lines[2]).startsWith("2,\"'=HYPERLINK(\"\"x\"\")\",");
        assertThat(csv).doesNotContain("Selam");
    }

    @Test
    void exportsAllMembersAndKeepsAmharicNamesIntact() throws Exception {
        when(getAllMembersUseCase.invoke()).thenReturn(List.of(member(1, "ፈለገ ሰላም"), member(2, "Abel")));

        MockHttpServletResponse response = export(get("/api/members/export"));
        byte[] bytes = response.getContentAsByteArray();
        String csv = body(response);

        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=members.csv");
        String[] lines = csv.substring(BOM.length()).strip().split("\\R");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("id,name,email,phone,joinDate,active,consecutiveMonthsMissed");
        assertThat(lines[1]).startsWith("1,ፈለገ ሰላም,");
        assertThat(lines[2]).startsWith("2,Abel,");
    }

    @Test
    void emptyIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/members/export").with(csrf())
                .with(user("v@example.com").roles("VOLUNTEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveIdReturns400() throws Exception {
        mockMvc.perform(post("/api/members/export").with(csrf())
                .with(user("v@example.com").roles("VOLUNTEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[0]}"))
            .andExpect(status().isBadRequest());
    }
}

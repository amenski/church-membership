package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemberController.class)
@Import({SecurityConfig.class, AuthProperties.class, MemberExportTest.SyncAsyncConfig.class})
class MemberExportTest {

    /** Runs StreamingResponseBody on the request thread so the body is complete when we read it. */
    @TestConfiguration
    static class SyncAsyncConfig {
        @Bean
        WebMvcConfigurer syncAsyncExecutor() {
            return new WebMvcConfigurer() {
                @Override
                public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
                    configurer.setTaskExecutor(new TaskExecutorAdapter(new SyncTaskExecutor()));
                }
            };
        }
    }

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

    private String exportBody(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/members/export")
                .with(user("v@example.com").roles("VOLUNTEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mockMvc.perform(asyncDispatch(result)).andReturn();
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getContentAsString();
    }

    @Test
    void exportsOnlyTheRequestedMembersWithFormulaGuard() throws Exception {
        when(getAllMembersUseCase.invoke()).thenReturn(List.of(
            member(1, "Abel"), member(2, "=HYPERLINK(\"x\")"), member(3, "Selam")));

        String csv = exportBody("{\"ids\":[1,2]}");
        String[] lines = csv.strip().split("\\R");

        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("id,name,email,phone,joinDate,active,consecutiveMonthsMissed");
        assertThat(lines[1]).startsWith("1,Abel,");
        assertThat(lines[2]).startsWith("2,\"'=HYPERLINK(\"\"x\"\")\",");
        assertThat(csv).doesNotContain("Selam");
    }

    @Test
    void emptyIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/members/export")
                .with(user("v@example.com").roles("VOLUNTEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void nonPositiveIdReturns400() throws Exception {
        mockMvc.perform(post("/api/members/export")
                .with(user("v@example.com").roles("VOLUNTEER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[0]}"))
            .andExpect(status().isBadRequest());
    }
}

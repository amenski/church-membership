package io.github.membertracker.infrastructure;

import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that bean-validation failures on the real controllers come back as a
 * 400 RFC 7807 problem, not just on a stand-in controller.
 */
@WebMvcTest(controllers = {MemberController.class, PaymentController.class})
@Import({SecurityConfig.class, AuthProperties.class})
class ValidationResponseTest {

    private static final String BAD_EMAIL = "not-an-email-xyz";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private RecordActivityUseCase recordActivityUseCase;
    @MockitoBean private GetAllMembersUseCase getAllMembersUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetActiveMembersUseCase getActiveMembersUseCase;
    @MockitoBean private GetInactiveMembersUseCase getInactiveMembersUseCase;
    @MockitoBean private GetArchivedMembersUseCase getArchivedMembersUseCase;
    @MockitoBean private SaveMemberUseCase saveMemberUseCase;
    @MockitoBean private UpdateMemberUseCase updateMemberUseCase;
    @MockitoBean private ArchiveMemberUseCase archiveMemberUseCase;
    @MockitoBean private DeleteMemberPermanentlyUseCase deleteMemberPermanentlyUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    @MockitoBean private GetAllPaymentsUseCase getAllPaymentsUseCase;
    @MockitoBean private GetPaymentByIdUseCase getPaymentByIdUseCase;
    @MockitoBean private GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    @MockitoBean private RecordPaymentUseCase recordPaymentUseCase;

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/payments/-1",
        "/api/payments/member/0",
        "/api/members/overdue/0",
        "/api/members/-5"
    })
    void invalidPathVariableReturns400Problem(String path) throws Exception {
        ResultActions result = mockMvc.perform(get(path).with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        result
            .andExpect(jsonPath("$.title").isNotEmpty())
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.errors[0].field").isNotEmpty())
            .andExpect(jsonPath("$.path").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void invalidMemberBodyListsFieldsWithoutEchoingRejectedValues() throws Exception {
        String body = "{\"name\":\"\",\"email\":\"" + BAD_EMAIL + "\",\"phone\":\"+390612345678\",\"joinDate\":\"2025-01-01\"}";

        String response = mockMvc.perform(post("/api/members").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'email')]").isNotEmpty())
            .andExpect(jsonPath("$.path").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(BAD_EMAIL);
    }
}

package io.github.membertracker.infrastructure;

import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A failing query is a 500 problem, never a 200 with zeros or empty lists. */
@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class DashboardErrorsTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetDashboardStatsUseCase getDashboardStatsUseCase;
    @MockitoBean private GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    @MockitoBean private GetRecentPaymentsUseCase getRecentPaymentsUseCase;
    @MockitoBean private GetCollectedByMonthUseCase getCollectedByMonthUseCase;
    @MockitoBean private GetRecentCommunicationsUseCase getRecentCommunicationsUseCase;

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {
        "/api/dashboard/stats",
        "/api/dashboard/recent-payments",
        "/api/dashboard/overdue-members",
        "/api/dashboard/recent-activities"
    })
    void aFailureIsAProblemJson500(String path) throws Exception {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database is down");
        when(getDashboardStatsUseCase.invoke()).thenThrow(failure);
        when(getMembersWithMissedPaymentsUseCase.invoke(1)).thenThrow(failure);
        when(getRecentPaymentsUseCase.invoke(org.mockito.ArgumentMatchers.anyInt())).thenThrow(failure);
        when(getRecentCommunicationsUseCase.invoke(org.mockito.ArgumentMatchers.anyInt())).thenThrow(failure);

        mockMvc.perform(get(path).with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}

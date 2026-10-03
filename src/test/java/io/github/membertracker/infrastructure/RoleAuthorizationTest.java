package io.github.membertracker.infrastructure;

import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Checks every role against every protected endpoint, so a role rename or a
 * missing role hierarchy fails here instead of in production.
 */
@WebMvcTest(controllers = {
    MemberController.class,
    PaymentController.class,
    CommunicationController.class,
    DashboardController.class
})
@Import({SecurityConfig.class, AuthProperties.class, RoleAuthorizationTest.SyncAsyncConfig.class})
class RoleAuthorizationTest {

    /** Runs StreamingResponseBody on the request thread so header writes cannot race. */
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

    // Lowest to highest; each role inherits everything below it
    private static final List<String> ROLES = List.of("MEMBER", "VOLUNTEER", "STAFF", "ADMIN");

    private static final String MEMBER_JSON =
        "{\"name\":\"Abel\",\"email\":\"abel@example.com\",\"phone\":\"+390612345678\",\"joinDate\":\"2025-01-01\",\"active\":true}";
    private static final String EXPORT_JSON = "{\"ids\":[1]}";
    private static final String PAYMENT_JSON =
        "{\"memberId\":1,\"amount\":50,\"paymentMethod\":\"CASH\"}";
    private static final String COMMUNICATION_JSON =
        "{\"title\":\"Feast day\",\"messageContent\":\"Service starts at 9.\",\"type\":\"ANNOUNCEMENT\"}";

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
    @MockitoBean private GetAllPaymentsUseCase getAllPaymentsUseCase;
    @MockitoBean private GetPaymentByIdUseCase getPaymentByIdUseCase;
    @MockitoBean private GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    @MockitoBean private RecordPaymentUseCase recordPaymentUseCase;
    @MockitoBean private GetAllCommunicationsUseCase getAllCommunicationsUseCase;
    @MockitoBean private GetCommunicationByIdUseCase getCommunicationByIdUseCase;
    @MockitoBean private CreateCommunicationUseCase createCommunicationUseCase;
    @MockitoBean private SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase;
    @MockitoBean private SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase;
    @MockitoBean private GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase;
    @MockitoBean private RetryDeliveryUseCase retryDeliveryUseCase;

    private record Endpoint(HttpMethod method, String path, String body, String minimumRole) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static List<Endpoint> endpoints() {
        return List.of(
            new Endpoint(HttpMethod.GET, "/api/members", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/members/1", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/members/active", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/members/inactive", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/members/overdue/1", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/members/export", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.POST, "/api/members/export", EXPORT_JSON, "VOLUNTEER"),
            new Endpoint(HttpMethod.POST, "/api/members", MEMBER_JSON, "STAFF"),
            new Endpoint(HttpMethod.PUT, "/api/members/1", MEMBER_JSON, "STAFF"),
            new Endpoint(HttpMethod.DELETE, "/api/members/1", null, "ADMIN"),

            new Endpoint(HttpMethod.GET, "/api/payments", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/payments/1", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/payments/member/1", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/payments/export", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.POST, "/api/payments", PAYMENT_JSON, "STAFF"),

            new Endpoint(HttpMethod.GET, "/api/communications", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/communications/1", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/communications/1/deliveries", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.POST, "/api/communications", COMMUNICATION_JSON, "STAFF"),
            new Endpoint(HttpMethod.POST, "/api/communications/send-to-all", COMMUNICATION_JSON, "STAFF"),
            new Endpoint(HttpMethod.POST, "/api/communications/send-to-overdue/1", COMMUNICATION_JSON, "STAFF"),
            new Endpoint(HttpMethod.POST, "/api/communications/send-to-member/1", COMMUNICATION_JSON, "STAFF"),
            new Endpoint(HttpMethod.POST, "/api/communications/1/deliveries/1/retry", null, "STAFF"),

            new Endpoint(HttpMethod.GET, "/api/dashboard/stats", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/dashboard/recent-payments", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/dashboard/overdue-members", null, "VOLUNTEER"),
            new Endpoint(HttpMethod.GET, "/api/dashboard/recent-activities", null, "VOLUNTEER")
        );
    }

    static Stream<Arguments> endpointsByRole() {
        return endpoints().stream()
            .flatMap(endpoint -> ROLES.stream().map(role -> Arguments.of(endpoint, role)));
    }

    static Stream<Endpoint> allEndpoints() {
        return endpoints().stream();
    }

    @ParameterizedTest(name = "{1} -> {0}")
    @MethodSource("endpointsByRole")
    void roleIsAllowedOnlyAtOrAboveMinimum(Endpoint endpoint, String role) throws Exception {
        boolean allowed = ROLES.indexOf(role) >= ROLES.indexOf(endpoint.minimumRole());

        MvcResult result = mockMvc.perform(buildRequest(endpoint).with(user("tester@example.com").roles(role)))
            .andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mockMvc.perform(asyncDispatch(result)).andReturn();
        }
        int status = result.getResponse().getStatus();

        if (allowed) {
            assertThat(status).as("%s should reach %s", role, endpoint).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s should be denied %s", role, endpoint).isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "anonymous -> {0}")
    @MethodSource("allEndpoints")
    void anonymousIsDenied(Endpoint endpoint) throws Exception {
        MvcResult result = mockMvc.perform(buildRequest(endpoint)).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result = mockMvc.perform(asyncDispatch(result)).andReturn();
        }
        int status = result.getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    private MockHttpServletRequestBuilder buildRequest(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path()).with(csrf());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return builder;
    }
}

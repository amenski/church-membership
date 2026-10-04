package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.HouseholdDomainException;
import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.CreateHouseholdUseCase;
import io.github.membertracker.usecase.DeleteHouseholdUseCase;
import io.github.membertracker.usecase.GetAllHouseholdsUseCase;
import io.github.membertracker.usecase.GetHouseholdByIdUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.UpdateHouseholdUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The household fixtures are the API contract the frontend builds on: the request fixture is what the form sends,
 * the response fixture is the exact JSON of a household (frontend/src tests read both).
 */
@WebMvcTest(controllers = HouseholdController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class HouseholdContractTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetAllHouseholdsUseCase getAllHouseholdsUseCase;
    @MockitoBean private GetHouseholdByIdUseCase getHouseholdByIdUseCase;
    @MockitoBean private CreateHouseholdUseCase createHouseholdUseCase;
    @MockitoBean private UpdateHouseholdUseCase updateHouseholdUseCase;
    @MockitoBean private DeleteHouseholdUseCase deleteHouseholdUseCase;

    private static String request() throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts/household-request.json"));
    }

    private static String response() throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts/household-response.json"));
    }

    private static Member member(long id, String name, MemberStatus status) {
        Member member = new Member(name, null, null);
        member.setId(id);
        member.setStatus(status);
        return member;
    }

    private static HouseholdDetails details(Member... members) {
        Household household = new Household("Kebede family", "Via Roma 1", "Scala B", "Roma", "00100", "Prefers calls after 18:00");
        household.setId(7L);
        return new HouseholdDetails(household, List.of(members));
    }

    private HouseholdDetails kebede() {
        return details(member(1, "Abebe Kebede", MemberStatus.MEMBER), member(2, "Tigist Kebede", MemberStatus.INACTIVE));
    }

    @Test
    void postFixtureIsAcceptedAndReachesTheUseCaseAndTheResponseMatchesTheFixture() throws Exception {
        when(createHouseholdUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(kebede());

        mockMvc.perform(post("/api/households").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isOk())
            .andExpect(content().json(response(), true));

        verify(createHouseholdUseCase).invoke("Kebede family", "Via Roma 1", "Scala B", "Roma", "00100", "Prefers calls after 18:00");
    }

    @Test
    void putFixtureReachesTheUseCaseWithThePathIdAndAnUnknownIdIsNotFound() throws Exception {
        when(updateHouseholdUseCase.invoke(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(updateHouseholdUseCase.invoke(org.mockito.ArgumentMatchers.eq(7L), any(), any(), any(), any(), any(), any()))
            .thenReturn(Optional.of(kebede()));

        mockMvc.perform(put("/api/households/7").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isOk())
            .andExpect(content().json(response(), true));
        mockMvc.perform(put("/api/households/99").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isNotFound());

        verify(updateHouseholdUseCase).invoke(7L, "Kebede family", "Via Roma 1", "Scala B", "Roma", "00100", "Prefers calls after 18:00");
    }

    @Test
    void getByIdAnswersTheFixtureShapeAndAnUnknownIdIsNotFound() throws Exception {
        when(getHouseholdByIdUseCase.invoke(7L)).thenReturn(Optional.of(kebede()));
        when(getHouseholdByIdUseCase.invoke(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/households/7").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk())
            .andExpect(content().json(response(), true));
        mockMvc.perform(get("/api/households/99").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isNotFound());
    }

    @Test
    void anOptionalFieldLeftOutOrBlankIsStoredAsNullAndTheNameIsTrimmed() throws Exception {
        when(createHouseholdUseCase.invoke(any(), any(), any(), any(), any(), any())).thenReturn(kebede());

        mockMvc.perform(post("/api/households").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"  Tesfaye family  \", \"city\": \"   \", \"notes\": \"\"}"))
            .andExpect(status().isOk());

        verify(createHouseholdUseCase).invoke("Tesfaye family", null, null, null, null, null);
    }

    @Test
    void aBlankNameAndTooLongFieldsAreFieldErrorsWithoutEchoingValues() throws Exception {
        String longCity = "C".repeat(101);
        String body = "{\"name\": \"   \", \"city\": \"" + longCity + "\", \"postalCode\": \"" + "9".repeat(21)
            + "\", \"addressLine1\": \"" + "A".repeat(101) + "\", \"addressLine2\": \"" + "B".repeat(101)
            + "\", \"notes\": \"" + "N".repeat(2001) + "\"}";

        String response = mockMvc.perform(post("/api/households").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'city')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'postalCode')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'addressLine1')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'addressLine2')]").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'notes')]").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(longCity).doesNotContain("NNNNNNNNNN");
        verify(createHouseholdUseCase, never()).invoke(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aMissingBodyAndANonPositiveIdAreRejected() throws Exception {
        mockMvc.perform(post("/api/households").with(csrf()).with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'name')]").isNotEmpty());
        mockMvc.perform(get("/api/households/0").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theListCarriesNameCityAndTheCountOfMembersTheCallerMaySee() throws Exception {
        when(getAllHouseholdsUseCase.invoke()).thenReturn(List.of(
            new HouseholdSummary(7L, "Kebede family", "Roma", 3, 1),
            new HouseholdSummary(8L, "Tesfaye family", null, 0, 0)));

        mockMvc.perform(get("/api/households").with(user("v@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk())
            .andExpect(content().json("[{\"id\":7,\"name\":\"Kebede family\",\"city\":\"Roma\",\"memberCount\":2},"
                + "{\"id\":8,\"name\":\"Tesfaye family\",\"city\":null,\"memberCount\":0}]", true));
        mockMvc.perform(get("/api/households").with(user("a@example.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].memberCount").value(3));
    }

    @Test
    void archivedMembersAreListedForAnAdminOnlyAndFlaggedByStatus() throws Exception {
        HouseholdDetails withArchived = details(member(1, "Abebe Kebede", MemberStatus.MEMBER),
            member(3, "Hidden Person", MemberStatus.ARCHIVED));
        when(getHouseholdByIdUseCase.invoke(7L)).thenReturn(Optional.of(withArchived));

        String staff = mockMvc.perform(get("/api/households/7").with(user("s@example.com").roles("STAFF")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        assertThat(staff).doesNotContain("Hidden Person");

        mockMvc.perform(get("/api/households/7").with(user("a@example.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[1].status").value("ARCHIVED"));
    }

    @Test
    void deleteAnswers200ForAnEmptyHouseholdAnd404ForAnUnknownOne() throws Exception {
        when(deleteHouseholdUseCase.invoke(7L)).thenReturn(true);
        when(deleteHouseholdUseCase.invoke(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/households/7").with(csrf()).with(user("a@example.com").roles("ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/households/99").with(csrf()).with(user("a@example.com").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteWhilePeopleAreAssignedIsA409WithTheCodeAndNoName() throws Exception {
        when(deleteHouseholdUseCase.invoke(7L)).thenThrow(HouseholdDomainException.hasPeople());

        mockMvc.perform(delete("/api/households/7").with(csrf()).with(user("a@example.com").roles("ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("HOUSEHOLD_002"))
            .andExpect(jsonPath("$.detail").value(
                "This household still has people. Move them to another household or remove them from it first."));
    }
}

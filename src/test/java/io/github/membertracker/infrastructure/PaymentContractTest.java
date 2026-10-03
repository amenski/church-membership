package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Payment;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The record-payment fixture is shared with the frontend test
 * (frontend/src/__tests__/utils/paymentPayload.test.js): both sides must agree on the body shape.
 */
@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class PaymentContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetAllPaymentsUseCase getAllPaymentsUseCase;
    @MockitoBean private GetPaymentByIdUseCase getPaymentByIdUseCase;
    @MockitoBean private GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    @MockitoBean private RecordPaymentUseCase recordPaymentUseCase;

    @Test
    void fixtureBodyIsAcceptedAndReachesTheUseCase() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/contracts/record-payment-request.json"));
        when(recordPaymentUseCase.invoke(any(), any(), any(), any(), any())).thenReturn(new Payment());

        mockMvc.perform(post("/api/payments").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture))
            .andExpect(status().isOk());

        verify(recordPaymentUseCase).invoke(eq(1L), eq(50.0), eq(PaymentMethod.CASH), eq(YearMonth.of(2026, 10)), any());
    }

    @Test
    void missingMemberIdIsRejectedWithTheFieldName() throws Exception {
        mockMvc.perform(post("/api/payments").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 50.0, \"paymentMethod\": \"CASH\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'memberId')]").isNotEmpty());

        verify(recordPaymentUseCase, never()).invoke(any(), any(), any(), any(), any());
    }

    @Test
    void unknownPaymentMethodIsRejected() throws Exception {
        mockMvc.perform(post("/api/payments").with(csrf())
                .with(user("s@example.com").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": 1, \"amount\": 50.0, \"paymentMethod\": \"BITCOIN\"}"))
            .andExpect(status().isBadRequest());

        verify(recordPaymentUseCase, never()).invoke(any(), any(), any(), any(), any());
    }
}

package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.PaymentMethod;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Payment;
import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.infrastructure.config.SecurityConfig;
import io.github.membertracker.usecase.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, AuthProperties.class})
class PaymentExportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private LoadUserByUsernameUseCase loadUserByUsernameUseCase;
    @MockitoBean private GetMemberByIdUseCase getMemberByIdUseCase;
    @MockitoBean private GetAllPaymentsUseCase getAllPaymentsUseCase;
    @MockitoBean private GetPaymentByIdUseCase getPaymentByIdUseCase;
    @MockitoBean private GetPaymentsByMemberUseCase getPaymentsByMemberUseCase;
    @MockitoBean private RecordPaymentUseCase recordPaymentUseCase;

    @Test
    void exportsAPlainUtf8CsvWithBomAndAmharicNames() throws Exception {
        Member member = new Member("ፈለገ ሰላም", "f@example.com", "+390612345678");
        member.setId(7L);
        Member formula = new Member("=SUM(A1)", "g@example.com", "+390612345678");
        formula.setId(8L);
        Payment first = new Payment(member, YearMonth.of(2026, 10), 50.0, PaymentMethod.CASH);
        first.setId(1L);
        Payment second = new Payment(formula, YearMonth.of(2023, 1), 25.5, PaymentMethod.BANK_TRANSFER);
        second.setId(2L);
        when(getAllPaymentsUseCase.invoke()).thenReturn(List.of(first, second));

        MockHttpServletResponse response = mockMvc.perform(get("/api/payments/export")
                .with(user("v@example.com").roles("VOLUNTEER")))
            .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=payments.csv");
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        String[] lines = new String(bytes, StandardCharsets.UTF_8).substring(1).strip().split("\\R");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("id,memberId,memberName,amount,paymentDate,period,method");
        assertThat(lines[1]).startsWith("1,7,ፈለገ ሰላም,50.00,").endsWith(",2026-10,CASH");
        assertThat(lines[2]).startsWith("2,8,'=SUM(A1),25.50,").endsWith(",2023-01,BANK_TRANSFER");
    }
}

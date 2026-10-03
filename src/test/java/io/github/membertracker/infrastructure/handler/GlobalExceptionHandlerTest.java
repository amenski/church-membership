package io.github.membertracker.infrastructure.handler;

import io.github.membertracker.domain.exception.DomainException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidBodyReturns400WithFieldErrorsAndNoRejectedValue() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"hunter2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(notNullValue()))
                .andExpect(jsonPath("$.path").value("/test/validate"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].message").value(notNullValue()))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void domainExceptionReturns400WithCodeAndMessage() throws Exception {
        mockMvc.perform(get("/test/domain"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TEST_CODE"))
                .andExpect(jsonPath("$.detail").value("Thing is not allowed"))
                .andExpect(jsonPath("$.path").value("/test/domain"));
    }

    @Test
    void accessDeniedReturns403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/test/forbidden"));
    }

    @Test
    void insufficientAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/test/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void constraintViolationReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value(notNullValue()))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void invalidPathVariableReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/param/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/param/-1"));
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/validate"));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));
    }

    record ValidatedBody(@Size(min = 8, message = "must be at least 8 characters") String password) {
    }

    record IdHolder(@Positive(message = "must be greater than 0") Long id) {
    }

    @RestController
    static class ThrowingController {

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody ValidatedBody body) {
            return "ok";
        }

        @GetMapping("/test/domain")
        String domain() {
            throw new DomainException("Thing is not allowed", "TEST_CODE", "Thing") {
            };
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/test/unauthenticated")
        String unauthenticated() {
            throw new InsufficientAuthenticationException("nope");
        }

        @GetMapping("/test/constraint")
        String constraint() {
            Set<ConstraintViolation<IdHolder>> violations = Validation.buildDefaultValidatorFactory()
                    .getValidator().validate(new IdHolder(-1L));
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/test/param/{id}")
        String param(@PathVariable @Positive Long id) {
            return "ok";
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new RuntimeException("secret-internal-detail");
        }
    }
}

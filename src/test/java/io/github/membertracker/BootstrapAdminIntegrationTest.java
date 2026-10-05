package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.membertracker.infrastructure.persistence.entity.UserEntity;
import io.github.membertracker.infrastructure.persistence.repository.UserJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * First start of a production database (default profile, real migrations, no sample data) with
 * BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD set: the startup runner creates exactly one enabled ADMIN, and
 * that administrator can sign in through the real security chain.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrapadmin;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.change-log=classpath:db/h2-master.xml",
        "app.bootstrap.admin-email=Owner@Example.org",
        "app.bootstrap.admin-password=Str0ng-Passw0rd!"
    })
@AutoConfigureMockMvc
class BootstrapAdminIntegrationTest {

    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;

    @Test
    void theStartupRunnerCreatedExactlyOneAdministrator() {
        List<UserEntity> users = userJpaRepository.findAll();

        assertThat(users).hasSize(1);
        UserEntity admin = users.get(0);
        assertThat(admin.getEmail()).isEqualTo("owner@example.org");
        assertThat(admin.getRole().name()).isEqualTo("ADMIN");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo("Str0ng-Passw0rd!");
        assertThat(passwordEncoder.matches("Str0ng-Passw0rd!", admin.getPassword())).isTrue();
    }

    @Test
    void theAdministratorCanSignIn() throws Exception {
        String xsrf = cookieValue(mockMvc.perform(get("/api/users/me")).andReturn().getResponse());

        mockMvc.perform(post("/api/auth/login")
                .cookie(new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@example.org\",\"password\":\"Str0ng-Passw0rd!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String setCookie = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(h -> h.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        return setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
    }
}

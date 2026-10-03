package io.github.membertracker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** The client IP used by the sign-in throttle may come from X-Forwarded-For only for loopback peers. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:proxytrust;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class ProxyTrustConfigTest {

    @Autowired
    private Environment environment;

    @Test
    void remoteIpHeaderIsXForwardedForAndOnlyLoopbackIsTrusted() {
        assertThat(environment.getProperty("server.tomcat.remoteip.remote-ip-header")).isEqualTo("X-Forwarded-For");
        String proxies = environment.getProperty("server.tomcat.remoteip.internal-proxies");
        System.out.println("RESOLVED internal-proxies = " + proxies);
        assertThat(proxies).isEqualTo("127\\.0\\.0\\.1|::1|0:0:0:0:0:0:0:1");
        assertThat("10.1.2.3".matches(proxies)).isFalse();
        assertThat("192.168.1.5".matches(proxies)).isFalse();
        assertThat("127.0.0.1".matches(proxies)).isTrue();
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}

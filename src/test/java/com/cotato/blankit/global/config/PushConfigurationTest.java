package com.cotato.blankit.global.config;

import com.cotato.blankit.domain.notification.push.service.PushNotificationContentFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PushConfigurationTest {

    @Test
    void productionProfileValidatesSchemaByDefault() throws Exception {
        var propertySource = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"))
                .getFirst();

        assertThat(propertySource.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("${JPA_DDL_AUTO:validate}");
    }

    @Test
    void notificationRoutesCanBeConfigured() {
        var factory = new PushNotificationContentFactory(
                "/calendar/tasks/{taskId}",
                "/quick-pack/{minutes}"
        );

        assertThat(factory.thirtyMinutePack(30).clickUrl()).isEqualTo("/quick-pack/30");
    }

    @Test
    void schedulerPolicyRejectsInvalidValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PushSchedulerProperties(
                        300_000,
                        0,
                        List.of(Duration.ofMinutes(1))
                ));
    }
}

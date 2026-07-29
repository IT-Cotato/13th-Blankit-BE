package com.cotato.blankit.global.config;

import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "blankit.firebase.enabled=false")
@ActiveProfiles("test")
class FirebaseDisabledContextTest {
    @Test
    void contextLoadsWithoutFirebaseInitialization() {
        assertThat(FirebaseApp.getApps()).isEmpty();
    }
}

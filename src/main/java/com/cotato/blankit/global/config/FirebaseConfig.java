package com.cotato.blankit.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "true")
    public FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getApps().get(0);
        }
        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault());
        if (properties.projectId() != null && !properties.projectId().isBlank()) {
            options.setProjectId(properties.projectId());
        }
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "blankit.firebase", name = "enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

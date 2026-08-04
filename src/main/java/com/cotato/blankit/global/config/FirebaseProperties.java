package com.cotato.blankit.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blankit.firebase")
public record FirebaseProperties(boolean enabled, String projectId) {
}

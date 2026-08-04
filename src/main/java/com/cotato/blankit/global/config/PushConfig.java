package com.cotato.blankit.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PushSchedulerProperties.class)
public class PushConfig {
}

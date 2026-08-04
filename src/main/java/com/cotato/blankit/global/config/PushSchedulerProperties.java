package com.cotato.blankit.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "blankit.push.scheduler")
public record PushSchedulerProperties(
        long processingLeaseMillis,
        int maxJobsPerTick,
        List<Duration> retryDelays
) {
    public PushSchedulerProperties {
        if (processingLeaseMillis <= 0) {
            throw new IllegalArgumentException("Push processing lease must be positive.");
        }
        if (maxJobsPerTick <= 0) {
            throw new IllegalArgumentException("Push max jobs per tick must be positive.");
        }
        if (retryDelays == null || retryDelays.isEmpty()
                || retryDelays.stream().anyMatch(delay -> delay == null || delay.isNegative() || delay.isZero())) {
            throw new IllegalArgumentException("Push retry delays must contain only positive durations.");
        }
        retryDelays = List.copyOf(retryDelays);
    }
}

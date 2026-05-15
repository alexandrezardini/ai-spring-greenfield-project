package com.streamtube.backend.common.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("streamtube.ratelimit.auth")
public record RateLimitProperties(
    int limitForPeriod, Duration limitRefreshPeriod, Duration timeoutDuration) {}

package com.streamtube.backend.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

  @Bean
  public RateLimiterRegistry rateLimiterRegistry(RateLimitProperties props) {
    RateLimiterConfig authConfig =
        RateLimiterConfig.custom()
            .limitForPeriod(props.limitForPeriod())
            .limitRefreshPeriod(props.limitRefreshPeriod())
            .timeoutDuration(props.timeoutDuration())
            .build();
    return RateLimiterRegistry.of(Map.of("auth", authConfig));
  }
}

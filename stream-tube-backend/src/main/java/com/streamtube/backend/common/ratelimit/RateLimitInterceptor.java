package com.streamtube.backend.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

  private final RateLimiterRegistry rateLimiterRegistry;
  private final RateLimitedPaths rateLimitedPaths;

  public RateLimitInterceptor(
      RateLimiterRegistry rateLimiterRegistry, RateLimitedPaths rateLimitedPaths) {
    this.rateLimiterRegistry = rateLimiterRegistry;
    this.rateLimitedPaths = rateLimitedPaths;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (!rateLimitedPaths.matches(request.getRequestURI())) {
      return true;
    }
    String ip = resolveClientIp(request);
    var rateLimiter = rateLimiterRegistry.rateLimiter("auth-" + ip, "auth");
    if (!rateLimiter.acquirePermission()) {
      throw new RateLimitExceededException();
    }
    return true;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

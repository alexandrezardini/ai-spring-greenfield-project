package com.streamtube.backend.common.ratelimit;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
public class RateLimitedPaths {

  private final Set<String> patterns = new CopyOnWriteArraySet<>();
  private final AntPathMatcher matcher = new AntPathMatcher();

  public void addPattern(String pattern) {
    patterns.add(pattern);
  }

  public boolean matches(String uri) {
    return patterns.stream().anyMatch(p -> matcher.match(p, uri));
  }
}

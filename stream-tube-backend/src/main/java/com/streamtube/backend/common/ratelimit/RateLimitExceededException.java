package com.streamtube.backend.common.ratelimit;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends DomainException {

  public RateLimitExceededException() {
    super(
        "RATE_LIMIT_EXCEEDED",
        HttpStatus.TOO_MANY_REQUESTS,
        "Too many requests. Please try again later.");
  }
}

package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class TokenReuseDetectedException extends DomainException {

  public TokenReuseDetectedException() {
    super("TOKEN_REUSE_DETECTED", HttpStatus.UNAUTHORIZED, "Refresh token reuse detected.");
  }
}

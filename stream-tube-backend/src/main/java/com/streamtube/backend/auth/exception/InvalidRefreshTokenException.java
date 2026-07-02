package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends DomainException {

  public InvalidRefreshTokenException() {
    super("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
  }
}

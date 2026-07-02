package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password.");
  }
}

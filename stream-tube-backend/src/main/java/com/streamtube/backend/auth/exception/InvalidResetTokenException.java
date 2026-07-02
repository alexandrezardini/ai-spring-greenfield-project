package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends DomainException {

  public InvalidResetTokenException() {
    super("INVALID_RESET_TOKEN", HttpStatus.NOT_FOUND, "Invalid or expired reset token.");
  }
}

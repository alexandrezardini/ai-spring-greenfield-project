package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidConfirmationTokenException extends DomainException {

  public InvalidConfirmationTokenException() {
    super(
        "INVALID_CONFIRMATION_TOKEN",
        HttpStatus.NOT_FOUND,
        "Invalid or expired confirmation token.");
  }
}

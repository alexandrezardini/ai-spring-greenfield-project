package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyConfirmedException extends DomainException {

  public EmailAlreadyConfirmedException() {
    super("EMAIL_ALREADY_CONFIRMED", HttpStatus.CONFLICT, "Email is already confirmed.");
  }
}

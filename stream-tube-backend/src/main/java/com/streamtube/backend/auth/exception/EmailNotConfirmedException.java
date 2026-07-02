package com.streamtube.backend.auth.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class EmailNotConfirmedException extends DomainException {

  public EmailNotConfirmedException() {
    super("EMAIL_NOT_CONFIRMED", HttpStatus.FORBIDDEN, "Email not confirmed.");
  }
}

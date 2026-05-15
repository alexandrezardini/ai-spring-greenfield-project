package com.streamtube.backend.users.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends DomainException {

  public EmailAlreadyExistsException() {
    super("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "Email address is already registered.");
  }
}

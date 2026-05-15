package com.streamtube.backend.users.exception;

import com.streamtube.backend.common.web.DomainException;
import org.springframework.http.HttpStatus;

public class HandleGenerationFailedException extends DomainException {

  public HandleGenerationFailedException() {
    super(
        "HANDLE_GENERATION_FAILED",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Could not generate a unique channel handle. Please try again.");
  }
}

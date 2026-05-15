package com.streamtube.backend.common.web;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus status;

  protected DomainException(String errorCode, HttpStatus status, String message) {
    super(message);
    this.errorCode = errorCode;
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getStatus() {
    return status;
  }
}

package com.streamtube.backend.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
    var body = new ApiErrorResponse(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage());
    return ResponseEntity.status(ex.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .orElse("Validation failed");
    return new ApiErrorResponse(400, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handleHandlerMethodValidation(HandlerMethodValidationException ex) {
    String message =
        ex.getAllErrors().stream()
            .findFirst()
            .map(e -> e.getDefaultMessage())
            .orElse("Validation failed");
    return new ApiErrorResponse(400, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiErrorResponse handleNoResourceFound(NoResourceFoundException ex) {
    return new ApiErrorResponse(404, "NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handleUnexpected(Exception ex) {
    return new ApiErrorResponse(500, "INTERNAL_ERROR", "An unexpected error occurred");
  }
}

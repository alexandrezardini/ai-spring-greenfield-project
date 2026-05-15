package com.streamtube.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ValidationTestController())
            .setControllerAdvice(handler)
            .build();
  }

  @Test
  void domainException_returnsExpectedShape() {
    ResponseEntity<ApiErrorResponse> response =
        handler.handleDomainException(new TestDomainException());

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(409);
    assertThat(response.getBody().error()).isEqualTo("TEST_ERROR");
    assertThat(response.getBody().message()).isEqualTo("Test error message");
  }

  @Test
  void fallbackException_returns500WithGenericMessage() {
    ApiErrorResponse response =
        handler.handleUnexpected(new RuntimeException("secret stack trace details"));

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(response.error()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.message()).doesNotContain("secret");
  }

  @Test
  void validationException_returns400WithValidationError() throws Exception {
    mockMvc
        .perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isString());
  }

  @RestController
  static class ValidationTestController {
    @PostMapping("/test/validation")
    ResponseEntity<Void> validate(@Valid @RequestBody TestRequest body) {
      return ResponseEntity.ok().build();
    }
  }

  record TestRequest(@NotBlank String name) {}

  static class TestDomainException extends DomainException {
    TestDomainException() {
      super("TEST_ERROR", HttpStatus.CONFLICT, "Test error message");
    }
  }
}

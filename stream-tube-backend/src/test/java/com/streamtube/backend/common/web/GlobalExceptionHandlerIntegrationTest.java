package com.streamtube.backend.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamtube.backend.common.ratelimit.RateLimitedPaths;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerIntegrationTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerIntegrationTest.TestController.class})
@ActiveProfiles("test")
@WithMockUser
class GlobalExceptionHandlerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RateLimiterRegistry rateLimiterRegistry;
  @MockitoBean private RateLimitedPaths rateLimitedPaths;

  @Test
  void domainException_returnsExpectedJsonShapeAndStatus() throws Exception {
    mockMvc
        .perform(get("/test-advice/domain"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.statusCode").value(409))
        .andExpect(jsonPath("$.error").value("TEST_ERROR"))
        .andExpect(jsonPath("$.message").value("Test error message"));
  }

  @Test
  void invalidBody_returns400WithValidationError() throws Exception {
    mockMvc
        .perform(
            post("/test-advice/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void uncaughtException_returns500WithInternalErrorAndNoStackTrace() throws Exception {
    mockMvc
        .perform(get("/test-advice/error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.statusCode").value(500))
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.trace").doesNotExist())
        .andExpect(jsonPath("$.path").doesNotExist());
  }

  @RestController
  @RequestMapping("/test-advice")
  static class TestController {
    @GetMapping("/domain")
    void throwDomain() {
      throw new TestDomainException();
    }

    @PostMapping("/validation")
    ResponseEntity<Void> validate(@Valid @RequestBody TestRequest body) {
      return ResponseEntity.ok().build();
    }

    @GetMapping("/error")
    void throwError() {
      throw new RuntimeException("internal details that must not leak");
    }
  }

  record TestRequest(@NotBlank String name) {}

  static class TestDomainException extends DomainException {
    TestDomainException() {
      super("TEST_ERROR", HttpStatus.CONFLICT, "Test error message");
    }
  }
}

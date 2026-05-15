package com.streamtube.backend.auth.web;

import com.streamtube.backend.auth.service.RegistrationService;
import com.streamtube.backend.common.ratelimit.RateLimitedPaths;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final RegistrationService registrationService;
  private final RateLimitedPaths rateLimitedPaths;

  public AuthController(RegistrationService registrationService, RateLimitedPaths rateLimitedPaths) {
    this.registrationService = registrationService;
    this.rateLimitedPaths = rateLimitedPaths;
  }

  @PostConstruct
  void registerRateLimitedPaths() {
    rateLimitedPaths.addPattern("/auth/register");
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = registrationService.register(request);
    return ResponseEntity.created(URI.create("/users/" + response.id())).body(response);
  }
}

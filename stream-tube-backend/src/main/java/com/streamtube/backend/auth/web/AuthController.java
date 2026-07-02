package com.streamtube.backend.auth.web;

import com.streamtube.backend.auth.service.EmailConfirmationService;
import com.streamtube.backend.auth.service.LoginService;
import com.streamtube.backend.auth.service.LogoutService;
import com.streamtube.backend.auth.service.PasswordResetRequestService;
import com.streamtube.backend.auth.service.PasswordResetService;
import com.streamtube.backend.auth.service.RefreshService;
import com.streamtube.backend.auth.service.RegistrationService;
import com.streamtube.backend.common.ratelimit.RateLimitedPaths;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final RegistrationService registrationService;
  private final EmailConfirmationService emailConfirmationService;
  private final LoginService loginService;
  private final RefreshService refreshService;
  private final LogoutService logoutService;
  private final PasswordResetRequestService passwordResetRequestService;
  private final PasswordResetService passwordResetService;
  private final RateLimitedPaths rateLimitedPaths;

  public AuthController(
      RegistrationService registrationService,
      EmailConfirmationService emailConfirmationService,
      LoginService loginService,
      RefreshService refreshService,
      LogoutService logoutService,
      PasswordResetRequestService passwordResetRequestService,
      PasswordResetService passwordResetService,
      RateLimitedPaths rateLimitedPaths) {
    this.registrationService = registrationService;
    this.emailConfirmationService = emailConfirmationService;
    this.loginService = loginService;
    this.refreshService = refreshService;
    this.logoutService = logoutService;
    this.passwordResetRequestService = passwordResetRequestService;
    this.passwordResetService = passwordResetService;
    this.rateLimitedPaths = rateLimitedPaths;
  }

  @PostConstruct
  void registerRateLimitedPaths() {
    rateLimitedPaths.addPattern("/auth/register");
    rateLimitedPaths.addPattern("/auth/login");
    rateLimitedPaths.addPattern("/auth/forgot-password");
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = registrationService.register(request);
    return ResponseEntity.created(URI.create("/users/" + response.id())).body(response);
  }

  @PostMapping("/confirm-email")
  public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
    emailConfirmationService.confirm(request.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(loginService.authenticate(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(refreshService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogoutRequest request) {
    logoutService.logout(UUID.fromString(jwt.getSubject()), request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetRequestService.requestReset(request.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.reset(request);
    return ResponseEntity.noContent().build();
  }
}

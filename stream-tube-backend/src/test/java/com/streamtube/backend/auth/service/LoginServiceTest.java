package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.exception.EmailNotConfirmedException;
import com.streamtube.backend.auth.exception.InvalidCredentialsException;
import com.streamtube.backend.auth.web.LoginRequest;
import com.streamtube.backend.auth.web.TokenPairResponse;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtAccessTokenService jwtAccessTokenService;
  @Mock private RefreshTokenService refreshTokenService;

  private LoginService loginService;

  @BeforeEach
  void setUp() {
    // passwordEncoder.encode(...) returns null from mock — dummyHash becomes null; acceptable in
    // unit tests since passwordEncoder.matches is also mocked
    loginService =
        new LoginService(
            userRepository, passwordEncoder, jwtAccessTokenService, refreshTokenService);
  }

  @Test
  void authenticate_validConfirmedUser_returnsTokenPair() {
    UUID userId = UUID.randomUUID();
    User user = confirmedUser(userId, "alice@example.com", "hash");

    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(jwtAccessTokenService.issue(userId)).thenReturn("jwt.token");
    when(refreshTokenService.issue(eq(userId), eq(null)))
        .thenReturn(
            new IssuedRefreshToken(
                UUID.randomUUID(),
                "rawtoken",
                Instant.now().plus(30, ChronoUnit.DAYS),
                UUID.randomUUID()));

    TokenPairResponse response =
        loginService.authenticate(new LoginRequest("alice@example.com", "secret"));

    assertThat(response.accessToken()).isEqualTo("jwt.token");
    assertThat(response.refreshToken()).isEqualTo("rawtoken");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(900L);
    verify(refreshTokenService).issue(userId, null);
  }

  @Test
  void authenticate_unknownEmail_runsTimingHashAndThrows() {
    when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> loginService.authenticate(new LoginRequest("unknown@example.com", "pass")))
        .isInstanceOf(InvalidCredentialsException.class);

    // timing parity: matches must still be called even when user is not found
    verify(passwordEncoder).matches(eq("pass"), any());
  }

  @Test
  void authenticate_wrongPassword_throwsInvalidCredentials() {
    UUID userId = UUID.randomUUID();
    User user = confirmedUser(userId, "bob@example.com", "hash");

    when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

    assertThatThrownBy(
            () -> loginService.authenticate(new LoginRequest("bob@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(jwtAccessTokenService, never()).issue(any());
  }

  @Test
  void authenticate_unconfirmedUser_throwsEmailNotConfirmed() {
    UUID userId = UUID.randomUUID();
    User user = User.create("unconf@example.com", "hash", "Unconf");
    user.setId(userId);
    // emailConfirmedAt is null — unconfirmed

    when(userRepository.findByEmail("unconf@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

    assertThatThrownBy(
            () -> loginService.authenticate(new LoginRequest("unconf@example.com", "pass")))
        .isInstanceOf(EmailNotConfirmedException.class);

    verify(refreshTokenService, never()).issue(any(), any());
  }

  private User confirmedUser(UUID id, String email, String hash) {
    User user = User.create(email, hash, "Name");
    user.setId(id);
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    return user;
  }
}

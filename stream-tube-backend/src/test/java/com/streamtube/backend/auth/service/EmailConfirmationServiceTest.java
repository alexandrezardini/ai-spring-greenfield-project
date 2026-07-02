package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.exception.EmailAlreadyConfirmedException;
import com.streamtube.backend.auth.exception.InvalidConfirmationTokenException;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.mapping.AggregateReference;

@ExtendWith(MockitoExtension.class)
class EmailConfirmationServiceTest {

  @Mock private OpaqueTokenGenerator opaqueTokenGenerator;
  @Mock private EmailTokenRepository emailTokenRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private EmailConfirmationService emailConfirmationService;

  private static final String RAW = "a".repeat(64);
  private static final String HASH = "b".repeat(64);

  @Test
  void confirm_validToken_confirmsUserAndMarksTokenUsed() {
    UUID userId = UUID.randomUUID();
    User user = User.create("alice@example.com", "hash", "Alice");
    user.setId(userId);
    EmailToken token = buildToken(userId, TokenType.CONFIRM_EMAIL, null, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatCode(() -> emailConfirmationService.confirm(RAW)).doesNotThrowAnyException();

    assertThat(user.getEmailConfirmedAt()).isNotNull();
    assertThat(token.getUsedAt()).isNotNull();
    verify(userRepository).save(user);
    verify(emailTokenRepository).save(token);
  }

  @Test
  void confirm_unknownToken_throwsInvalidConfirmationTokenException() {
    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> emailConfirmationService.confirm(RAW))
        .isInstanceOf(InvalidConfirmationTokenException.class);
  }

  @Test
  void confirm_wrongType_throwsInvalidConfirmationTokenException() {
    UUID userId = UUID.randomUUID();
    EmailToken token = buildToken(userId, TokenType.RESET_PASSWORD, null, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> emailConfirmationService.confirm(RAW))
        .isInstanceOf(InvalidConfirmationTokenException.class);
  }

  @Test
  void confirm_usedToken_throwsInvalidConfirmationTokenException() {
    UUID userId = UUID.randomUUID();
    Instant alreadyUsed = Instant.now().minus(5, ChronoUnit.MINUTES);
    EmailToken token = buildToken(userId, TokenType.CONFIRM_EMAIL, alreadyUsed, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> emailConfirmationService.confirm(RAW))
        .isInstanceOf(InvalidConfirmationTokenException.class);
  }

  @Test
  void confirm_expiredToken_throwsInvalidConfirmationTokenException() {
    UUID userId = UUID.randomUUID();
    Instant expired = Instant.now().minus(1, ChronoUnit.HOURS);
    EmailToken token = buildToken(userId, TokenType.CONFIRM_EMAIL, null, expired);

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> emailConfirmationService.confirm(RAW))
        .isInstanceOf(InvalidConfirmationTokenException.class);
  }

  @Test
  void confirm_alreadyConfirmedUser_throwsEmailAlreadyConfirmedException() {
    UUID userId = UUID.randomUUID();
    User user = User.create("confirmed@example.com", "hash", "Confirmed");
    user.setId(userId);
    user.setEmailConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
    EmailToken token = buildToken(userId, TokenType.CONFIRM_EMAIL, null, future());

    when(opaqueTokenGenerator.hash(RAW)).thenReturn(HASH);
    when(emailTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(token));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> emailConfirmationService.confirm(RAW))
        .isInstanceOf(EmailAlreadyConfirmedException.class);
  }

  private EmailToken buildToken(UUID userId, TokenType type, Instant usedAt, Instant expiresAt) {
    EmailToken token = EmailToken.issue(AggregateReference.to(userId), type, HASH, expiresAt);
    token.setUsedAt(usedAt);
    return token;
  }

  private Instant future() {
    return Instant.now().plus(24, ChronoUnit.HOURS);
  }
}

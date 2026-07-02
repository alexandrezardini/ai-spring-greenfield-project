package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.auth.persistence.EmailTokenRepository;
import com.streamtube.backend.auth.web.RegisterRequest;
import com.streamtube.backend.auth.web.RegisterResponse;
import com.streamtube.backend.common.email.AppProperties;
import com.streamtube.backend.common.email.EmailService;
import com.streamtube.backend.users.domain.Channel;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.ChannelRepository;
import com.streamtube.backend.users.service.UserRegistrationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  @Mock private UserRegistrationService userRegistrationService;
  @Mock private ChannelRepository channelRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private EmailTokenRepository emailTokenRepository;
  @Mock private OpaqueTokenGenerator tokenGenerator;
  @Mock private EmailService emailService;

  private final AppProperties appProperties = new AppProperties("http://localhost:8080");

  @InjectMocks private RegistrationService registrationService;

  @BeforeEach
  void setUp() {
    // inject the real AppProperties since @InjectMocks won't pick it up automatically
    registrationService =
        new RegistrationService(
            userRegistrationService,
            channelRepository,
            passwordEncoder,
            emailTokenRepository,
            tokenGenerator,
            emailService,
            appProperties);
  }

  @Test
  void register_happyPath_orchestratesAllStepsAndReturnsResponse() {
    User user = User.create("alice@example.com", "hash", "Alice");
    user.setId(UUID.randomUUID());
    Channel channel = Channel.create(AggregateReference.to(user.getId()), "alice", "Alice");
    channel.setId(UUID.randomUUID());

    when(passwordEncoder.encode("secret123")).thenReturn("encoded");
    when(userRegistrationService.register(eq("alice@example.com"), eq("encoded"), anyName()))
        .thenReturn(user);
    when(channelRepository.findByUserId(user.getId())).thenReturn(Optional.of(channel));
    when(tokenGenerator.generate())
        .thenReturn(new OpaqueTokenGenerator.Tokens("a".repeat(64), "b".repeat(64)));
    when(emailTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    RegisterRequest request = new RegisterRequest("alice@example.com", "secret123");
    RegisterResponse response = registrationService.register(request);

    assertThat(response.id()).isEqualTo(user.getId());
    assertThat(response.email()).isEqualTo("alice@example.com");
    assertThat(response.channel().handle()).isEqualTo("alice");

    verify(passwordEncoder).encode("secret123");
    verify(emailTokenRepository).save(argThat(t -> t.getType() == TokenType.CONFIRM_EMAIL));
    verify(emailService)
        .sendEmailConfirmation(
            eq("alice@example.com"),
            any(),
            argThat(link -> link.contains("confirm-email?token=" + "a".repeat(64))));
  }

  @Test
  void register_emailTokenPersistenceFails_emailNotSent() {
    User user = User.create("bob@example.com", "hash", "Bob");
    user.setId(UUID.randomUUID());
    Channel channel = Channel.create(AggregateReference.to(user.getId()), "bob", "Bob");
    channel.setId(UUID.randomUUID());

    when(passwordEncoder.encode(any())).thenReturn("encoded");
    when(userRegistrationService.register(any(), any(), any())).thenReturn(user);
    when(channelRepository.findByUserId(user.getId())).thenReturn(Optional.of(channel));
    when(tokenGenerator.generate())
        .thenReturn(new OpaqueTokenGenerator.Tokens("c".repeat(64), "d".repeat(64)));
    when(emailTokenRepository.save(any())).thenThrow(new DataAccessException("db error") {});

    assertThatThrownBy(
            () -> registrationService.register(new RegisterRequest("bob@example.com", "secret123")))
        .isInstanceOf(DataAccessException.class);

    verify(emailService, never()).sendEmailConfirmation(any(), any(), any());
  }

  private static String anyName() {
    return any();
  }
}

package com.streamtube.backend.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.exception.EmailAlreadyExistsException;
import com.streamtube.backend.users.exception.HandleGenerationFailedException;
import com.streamtube.backend.users.persistence.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ChannelHandleSaver channelHandleSaver;
  @Mock private HandleGenerator handleGenerator;
  @InjectMocks private UserRegistrationService service;

  private User stubUser(String email) {
    User user = User.create(email, "hash", "Alice");
    user.setId(UUID.randomUUID());
    return user;
  }

  @Test
  void register_happyPath_persistsUserAndChannel() {
    User saved = stubUser("alice@example.com");
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(userRepository.save(any())).thenReturn(saved);
    when(handleGenerator.fromEmail("alice@example.com")).thenReturn("alice");

    User result = service.register("alice@example.com", "hash", "Alice");

    assertThat(result).isEqualTo(saved);
    verify(channelHandleSaver).save(any(), argThat(h -> h.equals("alice")), anyString());
  }

  @Test
  void register_emailExists_throwsEmailAlreadyExistsException() {
    when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.register("bob@example.com", "hash", "Bob"))
        .isInstanceOf(EmailAlreadyExistsException.class);

    verify(userRepository, times(0)).save(any());
  }

  @Test
  void register_handleCollisionOnFirstAttempt_retriesWithSuffix() {
    User saved = stubUser("carol@example.com");
    when(userRepository.existsByEmail("carol@example.com")).thenReturn(false);
    when(userRepository.save(any())).thenReturn(saved);
    when(handleGenerator.fromEmail("carol@example.com")).thenReturn("carol");
    when(channelHandleSaver.save(any(), argThat(h -> "carol".equals(h)), anyString()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
    when(channelHandleSaver.save(
            any(), argThat(h -> h != null && h.startsWith("carol_")), anyString()))
        .thenReturn(null);

    User result = service.register("carol@example.com", "hash", "Carol");

    assertThat(result).isEqualTo(saved);
    verify(channelHandleSaver, times(2)).save(any(), anyString(), anyString());
  }

  @Test
  void register_fiveConsecutiveCollisions_throwsHandleGenerationFailedException() {
    User saved = stubUser("dave@example.com");
    when(userRepository.existsByEmail("dave@example.com")).thenReturn(false);
    when(userRepository.save(any())).thenReturn(saved);
    when(handleGenerator.fromEmail("dave@example.com")).thenReturn("dave");
    when(channelHandleSaver.save(any(), anyString(), anyString()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> service.register("dave@example.com", "hash", "Dave"))
        .isInstanceOf(HandleGenerationFailedException.class);

    verify(channelHandleSaver, times(5)).save(any(), anyString(), anyString());
  }
}

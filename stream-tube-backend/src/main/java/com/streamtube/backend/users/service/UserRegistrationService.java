package com.streamtube.backend.users.service;

import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.exception.EmailAlreadyExistsException;
import com.streamtube.backend.users.exception.HandleGenerationFailedException;
import com.streamtube.backend.users.persistence.UserRepository;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

  private static final int MAX_HANDLE_ATTEMPTS = 5;

  private final UserRepository userRepository;
  private final ChannelHandleSaver channelHandleSaver;
  private final HandleGenerator handleGenerator;
  private final SecureRandom secureRandom = new SecureRandom();

  public UserRegistrationService(
      UserRepository userRepository,
      ChannelHandleSaver channelHandleSaver,
      HandleGenerator handleGenerator) {
    this.userRepository = userRepository;
    this.channelHandleSaver = channelHandleSaver;
    this.handleGenerator = handleGenerator;
  }

  @Transactional
  public User register(String email, String passwordHash, String name) {
    if (userRepository.existsByEmail(email)) {
      throw new EmailAlreadyExistsException();
    }
    User user = userRepository.save(User.create(email, passwordHash, name));
    AggregateReference<User, UUID> userId = AggregateReference.to(user.getId());
    String baseHandle = handleGenerator.fromEmail(email);

    for (int attempt = 0; attempt < MAX_HANDLE_ATTEMPTS; attempt++) {
      String handle = (attempt == 0) ? baseHandle : baseHandle + "_" + randomHex3();
      try {
        channelHandleSaver.save(userId, handle, name);
        return user;
      } catch (DbActionExecutionException | DataIntegrityViolationException e) {
        // collision — retry with a suffix on the next attempt
      }
    }
    throw new HandleGenerationFailedException();
  }

  private String randomHex3() {
    byte[] bytes = new byte[2];
    secureRandom.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes).substring(0, 3);
  }
}

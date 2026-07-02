package com.streamtube.backend.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import com.streamtube.backend.common.persistence.DataJdbcConfig;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DataJdbcConfig.class})
@Transactional
class EmailTokenRepositoryIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private EmailTokenRepository emailTokenRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User savedUser(String email) {
    return userRepository.save(User.create(email, "hash", "Test User"));
  }

  private EmailToken savedToken(User user, TokenType type, String hash) {
    return emailTokenRepository.save(
        EmailToken.issue(
            AggregateReference.to(user.getId()),
            type,
            hash,
            Instant.now().plus(24, ChronoUnit.HOURS)));
  }

  @Test
  void saveAndFindByTokenHash_roundTrips() {
    User user = savedUser("alice@example.com");
    String hash = "a".repeat(64);
    savedToken(user, TokenType.CONFIRM_EMAIL, hash);

    var found = emailTokenRepository.findByTokenHash(hash);

    assertThat(found).isPresent();
    assertThat(found.get().getType()).isEqualTo(TokenType.CONFIRM_EMAIL);
  }

  @Test
  void invalidatePreviousFor_setsUsedAt_onMatchingRows() {
    User user = savedUser("bob@example.com");
    savedToken(user, TokenType.CONFIRM_EMAIL, "b".repeat(64));
    savedToken(user, TokenType.CONFIRM_EMAIL, "c".repeat(64));
    savedToken(user, TokenType.RESET_PASSWORD, "d".repeat(64));

    Instant when = Instant.now();
    int count =
        emailTokenRepository.invalidatePreviousFor(user.getId(), TokenType.CONFIRM_EMAIL, when);

    assertThat(count).isEqualTo(2);
    var confirmTokens = (List<EmailToken>) emailTokenRepository.findAll();
    confirmTokens.stream()
        .filter(t -> t.getType() == TokenType.CONFIRM_EMAIL)
        .forEach(t -> assertThat(t.getUsedAt()).isNotNull());
    confirmTokens.stream()
        .filter(t -> t.getType() == TokenType.RESET_PASSWORD)
        .forEach(t -> assertThat(t.getUsedAt()).isNull());
  }

  @Test
  void checkConstraint_rejectsInvalidTokenType() {
    User user = savedUser("carol@example.com");
    String userId = user.getId().toString();
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO email_tokens (user_id, type, token_hash, expires_at)"
                        + " VALUES (?::uuid, ?, ?, now() + interval '1 hour')",
                    userId,
                    "OTHER",
                    "e".repeat(64)))
        .isInstanceOf(DataAccessException.class);
  }
}

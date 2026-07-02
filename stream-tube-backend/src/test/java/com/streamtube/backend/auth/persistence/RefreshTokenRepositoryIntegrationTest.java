package com.streamtube.backend.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.auth.domain.RefreshToken;
import com.streamtube.backend.common.persistence.DataJdbcConfig;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DataJdbcConfig.class})
@Transactional
class RefreshTokenRepositoryIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  private User savedUser(String email) {
    return userRepository.save(User.create(email, "hash", "Test User"));
  }

  private RefreshToken savedToken(User user, UUID familyId, String hash) {
    return refreshTokenRepository.save(
        RefreshToken.issue(
            AggregateReference.to(user.getId()),
            familyId,
            hash,
            Instant.now().plus(30, ChronoUnit.DAYS)));
  }

  @Test
  void saveAndFindByTokenHash_roundTrips() {
    User user = savedUser("alice@example.com");
    UUID familyId = UUID.randomUUID();
    String hash = "a".repeat(64);
    savedToken(user, familyId, hash);

    var found = refreshTokenRepository.findByTokenHash(hash);

    assertThat(found).isPresent();
    assertThat(found.get().getFamilyId()).isEqualTo(familyId);
  }

  @Test
  void revokeFamily_setsRevokedAtAndReason_onAllFamilyMembers() {
    User user = savedUser("bob@example.com");
    UUID familyId = UUID.randomUUID();
    savedToken(user, familyId, "b".repeat(64));
    savedToken(user, familyId, "c".repeat(64));

    Instant when = Instant.now();
    int count = refreshTokenRepository.revokeFamily(familyId, "TOKEN_REUSE", when);

    assertThat(count).isEqualTo(2);
    List<RefreshToken> family = refreshTokenRepository.findByFamilyId(familyId);
    assertThat(family)
        .allSatisfy(
            t -> {
              assertThat(t.getRevokedAt()).isNotNull();
              assertThat(t.getRevokedReason()).isEqualTo("TOKEN_REUSE");
            });
  }

  @Test
  void revokeFamily_doesNotUpdateAlreadyRevokedRows() {
    User user = savedUser("carol@example.com");
    UUID familyId = UUID.randomUUID();
    RefreshToken token = savedToken(user, familyId, "d".repeat(64));
    Instant firstRevoke = Instant.now().minus(5, ChronoUnit.MINUTES);
    token.setRevokedAt(firstRevoke);
    token.setRevokedReason("USER_LOGOUT");
    refreshTokenRepository.save(token);

    int count = refreshTokenRepository.revokeFamily(familyId, "TOKEN_REUSE", Instant.now());

    assertThat(count).isZero();
    RefreshToken updated = refreshTokenRepository.findById(token.getId()).orElseThrow();
    assertThat(updated.getRevokedReason()).isEqualTo("USER_LOGOUT");
  }

  @Test
  void deletingUser_cascadesToRefreshTokens() {
    User user = savedUser("dave@example.com");
    UUID familyId = UUID.randomUUID();
    RefreshToken token = savedToken(user, familyId, "e".repeat(64));
    UUID tokenId = token.getId();

    userRepository.delete(user);

    assertThat(refreshTokenRepository.findById(tokenId)).isEmpty();
  }
}

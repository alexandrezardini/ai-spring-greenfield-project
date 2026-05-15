package com.streamtube.backend.auth.persistence;

import com.streamtube.backend.auth.domain.EmailToken;
import com.streamtube.backend.auth.domain.TokenType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends CrudRepository<EmailToken, UUID> {

  Optional<EmailToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "UPDATE email_tokens SET used_at = :when"
          + " WHERE user_id = :userId AND type = :type AND used_at IS NULL")
  int invalidatePreviousFor(
      @Param("userId") UUID userId, @Param("type") TokenType type, @Param("when") Instant when);
}

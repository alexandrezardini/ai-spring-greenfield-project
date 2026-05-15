package com.streamtube.backend.auth.persistence;

import com.streamtube.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamilyId(UUID familyId);

  @Modifying
  @Query(
      "UPDATE refresh_tokens SET revoked_at = :when, revoked_reason = :reason"
          + " WHERE family_id = :familyId AND revoked_at IS NULL")
  int revokeFamily(
      @Param("familyId") UUID familyId,
      @Param("reason") String reason,
      @Param("when") Instant when);
}

package com.streamtube.backend.auth.domain;

import com.streamtube.backend.users.domain.User;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Table;

@Table("refresh_tokens")
@Getter
@Setter
public class RefreshToken {

  @Id private UUID id;
  private AggregateReference<User, UUID> userId;
  private UUID familyId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant rotatedAt;
  private UUID rotatedToId;
  private Instant revokedAt;
  private String revokedReason;
  private Instant createdAt;

  public static RefreshToken issue(
      AggregateReference<User, UUID> userId, UUID familyId, String tokenHash, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.userId = userId;
    token.familyId = familyId;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    token.createdAt = Instant.now();
    return token;
  }
}

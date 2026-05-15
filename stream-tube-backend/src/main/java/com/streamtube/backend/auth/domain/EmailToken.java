package com.streamtube.backend.auth.domain;

import com.streamtube.backend.users.domain.User;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Table;

@Table("email_tokens")
@Getter
@Setter
public class EmailToken {

  @Id private UUID id;
  private AggregateReference<User, UUID> userId;
  private TokenType type;
  private String tokenHash;
  private Instant expiresAt;
  private Instant usedAt;
  private Instant createdAt;

  public static EmailToken issue(
      AggregateReference<User, UUID> userId, TokenType type, String tokenHash, Instant expiresAt) {
    EmailToken token = new EmailToken();
    token.userId = userId;
    token.type = type;
    token.tokenHash = tokenHash;
    token.expiresAt = expiresAt;
    token.createdAt = Instant.now();
    return token;
  }
}

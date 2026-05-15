package com.streamtube.backend.users.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
@Getter
@Setter
public class User {

  @Id private UUID id;
  private String email;
  private String passwordHash;
  private String name;
  private Instant emailConfirmedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public static User create(String email, String passwordHash, String name) {
    User user = new User();
    user.email = email;
    user.passwordHash = passwordHash;
    user.name = name;
    Instant now = Instant.now();
    user.createdAt = now;
    user.updatedAt = now;
    return user;
  }
}

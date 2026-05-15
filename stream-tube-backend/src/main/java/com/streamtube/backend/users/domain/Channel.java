package com.streamtube.backend.users.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Table;

@Table("channels")
@Getter
@Setter
public class Channel {

  @Id private UUID id;
  private AggregateReference<User, UUID> userId;
  private String handle;
  private String name;
  private String description;
  private Instant createdAt;
  private Instant updatedAt;

  public static Channel create(AggregateReference<User, UUID> userId, String handle, String name) {
    Channel channel = new Channel();
    channel.userId = userId;
    channel.handle = handle;
    channel.name = name;
    Instant now = Instant.now();
    channel.createdAt = now;
    channel.updatedAt = now;
    return channel;
  }
}

package com.streamtube.backend.users.persistence;

import com.streamtube.backend.users.domain.Channel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface ChannelRepository extends CrudRepository<Channel, UUID> {

  Optional<Channel> findByUserId(UUID userId);

  Optional<Channel> findByHandle(String handle);
}

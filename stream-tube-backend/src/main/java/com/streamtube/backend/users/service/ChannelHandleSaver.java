package com.streamtube.backend.users.service;

import com.streamtube.backend.users.domain.Channel;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.ChannelRepository;
import java.util.UUID;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChannelHandleSaver {

  private final ChannelRepository channelRepository;

  ChannelHandleSaver(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
  }

  @Transactional(propagation = Propagation.NESTED)
  Channel save(AggregateReference<User, UUID> userId, String handle, String name) {
    return channelRepository.save(Channel.create(userId, handle, name));
  }
}

package com.streamtube.backend.users.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.common.persistence.DataJdbcConfig;
import com.streamtube.backend.users.domain.Channel;
import com.streamtube.backend.users.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DataJdbcConfig.class})
@Transactional
class ChannelRepositoryIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private ChannelRepository channelRepository;

  private User savedUser(String email) {
    return userRepository.save(User.create(email, "hash", "Test User"));
  }

  @Test
  void findByUserId_returnsChannel() {
    User user = savedUser("dave@example.com");
    Channel channel =
        Channel.create(AggregateReference.to(user.getId()), "davechannel", "Dave's Channel");
    channelRepository.save(channel);

    var found = channelRepository.findByUserId(user.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getHandle()).isEqualTo("davechannel");
  }

  @Test
  void findByHandle_returnsChannel() {
    User user = savedUser("eve@example.com");
    Channel channel =
        Channel.create(AggregateReference.to(user.getId()), "evechannel", "Eve's Channel");
    channelRepository.save(channel);

    assertThat(channelRepository.findByHandle("evechannel")).isPresent();
    assertThat(channelRepository.findByHandle("nonexistent")).isEmpty();
  }

  @Test
  void duplicateUserId_violatesUniqueConstraint() {
    User user = savedUser("frank@example.com");
    channelRepository.save(
        Channel.create(AggregateReference.to(user.getId()), "frank1", "Frank 1"));

    assertThatThrownBy(
            () ->
                channelRepository.save(
                    Channel.create(AggregateReference.to(user.getId()), "frank2", "Frank 2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void duplicateHandle_violatesUniqueConstraint() {
    User user1 = savedUser("grace@example.com");
    User user2 = savedUser("heidi@example.com");
    channelRepository.save(
        Channel.create(AggregateReference.to(user1.getId()), "sharedhandle", "Grace"));

    assertThatThrownBy(
            () ->
                channelRepository.save(
                    Channel.create(AggregateReference.to(user2.getId()), "sharedhandle", "Heidi")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingUser_cascadesToChannel() {
    User user = savedUser("ivan@example.com");
    Channel channel =
        Channel.create(AggregateReference.to(user.getId()), "ivanchannel", "Ivan's Channel");
    channelRepository.save(channel);
    UUID channelId = channelRepository.findByUserId(user.getId()).get().getId();

    userRepository.delete(user);

    assertThat(channelRepository.findById(channelId)).isEmpty();
  }
}

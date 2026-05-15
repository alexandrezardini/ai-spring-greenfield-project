package com.streamtube.backend.users.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamtube.backend.TestcontainersConfiguration;
import com.streamtube.backend.common.persistence.DataJdbcConfig;
import com.streamtube.backend.users.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, DataJdbcConfig.class})
@Transactional
class UserRepositoryIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Test
  void saveAndFindByEmail_roundTrips() {
    User user = User.create("alice@example.com", "hash", "Alice");
    userRepository.save(user);

    var found = userRepository.findByEmail("alice@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
  }

  @Test
  void findByEmail_isCaseInsensitive_dueToCitext() {
    User user = User.create("alice2@example.com", "hash", "Alice");
    userRepository.save(user);

    var found = userRepository.findByEmail("Alice2@Example.COM");

    assertThat(found).isPresent();
  }

  @Test
  void existsByEmail_returnsTrueForInsertedRow() {
    User user = User.create("bob@example.com", "hash", "Bob");
    userRepository.save(user);

    assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
  }

  @Test
  void savingDuplicateEmail_differentCase_violatesUniqueConstraint() {
    userRepository.save(User.create("carol@example.com", "hash", "Carol"));

    assertThatThrownBy(
            () -> userRepository.save(User.create("Carol@example.com", "hash2", "Carol2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}

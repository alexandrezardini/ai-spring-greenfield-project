package com.streamtube.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LiquibaseExtensionsIntegrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void pgcryptoExtensionIsInstalled() {
    List<String> extensions =
      jdbcTemplate.queryForList(
        "SELECT extname FROM pg_extension WHERE extname = 'pgcrypto'", String.class);
    assertThat(extensions).containsExactly("pgcrypto");
  }

  @Test
  void citextExtensionIsInstalled() {
    List<String> extensions =
      jdbcTemplate.queryForList(
        "SELECT extname FROM pg_extension WHERE extname = 'citext'", String.class);
    assertThat(extensions).containsExactly("citext");
  }
}

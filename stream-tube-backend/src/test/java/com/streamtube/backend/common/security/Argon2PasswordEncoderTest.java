package com.streamtube.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class Argon2PasswordEncoderTest {

  private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);

  @Test
  void encode_producesArgon2idHash() {
    String hash = encoder.encode("mySecret123");
    assertThat(hash).startsWith("$argon2id$");
  }

  @Test
  void matches_returnsTrueForCorrectPassword() {
    String hash = encoder.encode("mySecret123");
    assertThat(encoder.matches("mySecret123", hash)).isTrue();
  }

  @Test
  void matches_returnsFalseForWrongPassword() {
    String hash = encoder.encode("mySecret123");
    assertThat(encoder.matches("wrongPassword", hash)).isFalse();
  }

  @Test
  void matches_returnsFalseForAlteredHash() {
    String hash = encoder.encode("mySecret123");
    String alteredHash = hash.replace(hash.charAt(hash.length() - 1), 'X');
    assertThat(encoder.matches("mySecret123", alteredHash)).isFalse();
  }
}

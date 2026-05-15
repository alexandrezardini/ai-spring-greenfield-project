package com.streamtube.backend.users.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HandleGeneratorTest {

  private final HandleGenerator generator = new HandleGenerator();

  @Test
  void fromEmail_lowercasesAndStripsAllowedChars() {
    assertThat(generator.fromEmail("Alice.Smith@example.com")).isEqualTo("alicesmith");
  }

  @Test
  void fromEmail_preservesUnderscoresAndDigits() {
    assertThat(generator.fromEmail("user_42@example.com")).isEqualTo("user_42");
  }

  @Test
  void fromEmail_mixedAsciiAndNonAscii_keepsAsciiPart() {
    assertThat(generator.fromEmail("jöão@example.com")).isEqualTo("jo");
  }

  @Test
  void fromEmail_purelyNonAsciiPrefix_fallsBackToUserHex() {
    assertThat(generator.fromEmail("日本@example.com")).matches("user_[0-9a-f]{8}");
    assertThat(generator.fromEmail("ñöäü@example.com")).matches("user_[0-9a-f]{8}");
  }

  @Test
  void fromEmail_specialCharOnlyPrefix_fallsBackToUserHex() {
    assertThat(generator.fromEmail("!!@example.com")).matches("user_[0-9a-f]{8}");
    assertThat(generator.fromEmail("---@example.com")).matches("user_[0-9a-f]{8}");
  }

  @Test
  void fromEmail_truncatesTo46Chars() {
    String result =
        generator.fromEmail(
            "a-really-long-prefix-with-many-characters-to-trigger-truncation@x.com");
    assertThat(result).hasSize(46);
  }

  @Test
  void fromEmail_shortPrefix_notTruncated() {
    String result = generator.fromEmail("abc@x.com");
    assertThat(result).isEqualTo("abc");
    assertThat(result.length()).isLessThanOrEqualTo(46);
  }
}

package com.streamtube.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamtube.backend.auth.service.OpaqueTokenGenerator.Tokens;
import org.junit.jupiter.api.Test;

class OpaqueTokenGeneratorTest {

  private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

  @Test
  void generate_producesRawHexOf64Chars() {
    Tokens tokens = generator.generate();
    assertThat(tokens.rawHex()).hasSize(64);
  }

  @Test
  void generate_producesSha256HashOf64Chars() {
    Tokens tokens = generator.generate();
    assertThat(tokens.sha256HexHash()).hasSize(64);
  }

  @Test
  void hash_isDeterministicAndMatchesGeneratedHash() {
    Tokens tokens = generator.generate();
    String recomputed = generator.hash(tokens.rawHex());
    assertThat(recomputed).isEqualTo(tokens.sha256HexHash());
  }

  @Test
  void generate_producesDifferentRawValuesAcrossCalls() {
    Tokens first = generator.generate();
    Tokens second = generator.generate();
    assertThat(first.rawHex()).isNotEqualTo(second.rawHex());
  }
}

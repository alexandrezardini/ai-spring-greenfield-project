package com.streamtube.backend.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class OpaqueTokenGenerator {

  public record Tokens(String rawHex, String sha256HexHash) {}

  public Tokens generate() {
    try {
      byte[] bytes = new byte[32];
      SecureRandom.getInstanceStrong().nextBytes(bytes);
      String rawHex = HexFormat.of().formatHex(bytes);
      return new Tokens(rawHex, hash(rawHex));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No strong SecureRandom algorithm available", e);
    }
  }

  public String hash(String rawHex) {
    try {
      byte[] rawBytes = HexFormat.of().parseHex(rawHex);
      byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(rawBytes);
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

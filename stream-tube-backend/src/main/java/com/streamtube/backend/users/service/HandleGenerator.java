package com.streamtube.backend.users.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class HandleGenerator {

  private static final int MAX_LENGTH = 46;
  private final SecureRandom secureRandom = new SecureRandom();

  public String fromEmail(String email) {
    String prefix = email.substring(0, email.indexOf('@'));
    String normalized = prefix.toLowerCase().replaceAll("[^a-z0-9_]", "");
    if (normalized.isEmpty()) {
      return fallback();
    }
    return normalized.length() > MAX_LENGTH ? normalized.substring(0, MAX_LENGTH) : normalized;
  }

  private String fallback() {
    byte[] bytes = new byte[4];
    secureRandom.nextBytes(bytes);
    return "user_" + HexFormat.of().formatHex(bytes);
  }
}

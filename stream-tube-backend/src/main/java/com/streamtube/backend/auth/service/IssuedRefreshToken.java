package com.streamtube.backend.auth.service;

import java.time.Instant;
import java.util.UUID;

public record IssuedRefreshToken(UUID tokenId, String rawHex, Instant expiresAt, UUID familyId) {}

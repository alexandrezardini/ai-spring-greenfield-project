package com.streamtube.backend.auth.web;

public record TokenPairResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {}

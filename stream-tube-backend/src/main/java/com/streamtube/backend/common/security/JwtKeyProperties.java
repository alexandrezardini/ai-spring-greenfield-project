package com.streamtube.backend.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("streamtube.security.jwt")
public record JwtKeyProperties(String privateKey, String publicKey) {}

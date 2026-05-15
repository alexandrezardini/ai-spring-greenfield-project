package com.streamtube.backend.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("streamtube.app")
public record AppProperties(String publicUrl) {}

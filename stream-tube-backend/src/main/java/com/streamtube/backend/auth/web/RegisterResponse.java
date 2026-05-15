package com.streamtube.backend.auth.web;

import java.util.UUID;

public record RegisterResponse(UUID id, String email, ChannelView channel) {

  public record ChannelView(UUID id, String handle, String name) {}
}

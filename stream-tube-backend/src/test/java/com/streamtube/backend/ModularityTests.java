package com.streamtube.backend;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

  @Test
  void verifyModularity() {
    ApplicationModules.of(StreamTubeBackendApplication.class).verify();
  }
}

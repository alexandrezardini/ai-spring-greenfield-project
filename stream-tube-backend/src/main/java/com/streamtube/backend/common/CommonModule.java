package com.streamtube.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommonModule {

  public void logStartup() {
    log.info("Common module initialized");
  }
}

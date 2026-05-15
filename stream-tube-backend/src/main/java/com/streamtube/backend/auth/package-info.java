@ApplicationModule(
    displayName = "Auth",
    allowedDependencies = {"users::domain", "users::persistence", "users::service", "common"})
package com.streamtube.backend.auth;

import org.springframework.modulith.ApplicationModule;

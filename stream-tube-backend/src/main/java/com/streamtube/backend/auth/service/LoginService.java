package com.streamtube.backend.auth.service;

import com.streamtube.backend.auth.exception.EmailNotConfirmedException;
import com.streamtube.backend.auth.exception.InvalidCredentialsException;
import com.streamtube.backend.auth.web.LoginRequest;
import com.streamtube.backend.auth.web.TokenPairResponse;
import com.streamtube.backend.users.domain.User;
import com.streamtube.backend.users.persistence.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtAccessTokenService jwtAccessTokenService;
  private final RefreshTokenService refreshTokenService;
  private final String dummyHash;

  public LoginService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtAccessTokenService jwtAccessTokenService,
      RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtAccessTokenService = jwtAccessTokenService;
    this.refreshTokenService = refreshTokenService;
    // precomputed once at startup — used solely to keep unknown-email timing uniform
    this.dummyHash = passwordEncoder.encode("dummy_timing_placeholder");
  }

  @Transactional
  public TokenPairResponse authenticate(LoginRequest request) {
    Optional<User> userOpt = userRepository.findByEmail(request.email());
    if (userOpt.isEmpty()) {
      passwordEncoder.matches(request.password(), dummyHash);
      throw new InvalidCredentialsException();
    }
    User user = userOpt.get();
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    if (user.getEmailConfirmedAt() == null) {
      throw new EmailNotConfirmedException();
    }
    String accessToken = jwtAccessTokenService.issue(user.getId());
    IssuedRefreshToken issued = refreshTokenService.issue(user.getId(), null);
    return new TokenPairResponse(accessToken, issued.rawHex(), "Bearer", 900L);
  }
}

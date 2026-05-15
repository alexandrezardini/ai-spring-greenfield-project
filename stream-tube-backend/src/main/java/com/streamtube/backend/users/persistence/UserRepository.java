package com.streamtube.backend.users.persistence;

import com.streamtube.backend.users.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends CrudRepository<User, UUID> {

  @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email)")
  Optional<User> findByEmail(@Param("email") String email);

  @Query("SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email) = LOWER(:email))")
  boolean existsByEmail(@Param("email") String email);
}

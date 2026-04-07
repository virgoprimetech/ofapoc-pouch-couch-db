package com.primetech.poc.identity.features.user.get;

import com.primetech.poc.identity.features.user.create.UserResponse;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Get User Endpoint
 * <p>
 * REST API endpoint for retrieving users.
 */
@RestController
@RequestMapping("/users")
class GetUserEndpoint {

  private final GetUserHandler handler;
  private final UserMapper userMapper;

  public GetUserEndpoint(GetUserHandler handler, UserMapper userMapper) {
    this.handler = handler;
    this.userMapper = userMapper;
  }

  /**
   * Get a user by ID
   */
  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> get(@PathVariable UUID id) {
    GetUserQuery query = new GetUserQuery(id);
    User user = handler.handle(query);
    return ResponseEntity.ok(userMapper.toResponse(user));
  }
}

package com.primetech.poc.identity.features.user.update;

import com.primetech.poc.identity.features.user.create.UserResponse;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Update User Endpoint
 * <p>
 * REST API endpoint for updating users.
 */
@RestController
@RequestMapping("/users")
class UpdateUserEndpoint {

  private final UpdateUserHandler handler;
  private final UserMapper userMapper;

  public UpdateUserEndpoint(UpdateUserHandler handler, UserMapper userMapper) {
    this.handler = handler;
    this.userMapper = userMapper;
  }

  /**
   * Update an existing user
   */
  @PutMapping("/{id}")
  public ResponseEntity<UserResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRequest request
  ) {
    UpdateUserCommand command = userMapper.toCommand(request, id);
    User user = handler.handle(command);
    return ResponseEntity.ok(userMapper.toResponse(user));
  }
}

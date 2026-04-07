package com.primetech.poc.identity.features.user.create;

import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create User Endpoint
 * <p>
 * REST API endpoint for creating users.
 */
@RestController
@RequestMapping("/users")
class CreateUserEndpoint {

  private final CreateUserHandler handler;
  private final UserMapper userMapper;

  public CreateUserEndpoint(CreateUserHandler handler, UserMapper userMapper) {
    this.handler = handler;
    this.userMapper = userMapper;
  }

  /**
   * Create a new user
   */
  @PostMapping
  public ResponseEntity<UserResponse> create(
      @Valid @RequestBody CreateUserRequest request
  ) {
    CreateUserCommand command = userMapper.toCommand(request);
    User user = handler.handle(command);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(userMapper.toResponse(user));
  }
}

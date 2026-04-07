package com.primetech.poc.identity.features.user.list;

import com.primetech.poc.identity.features.user.create.UserResponse;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.UserStatus;
import com.primetech.poc.identity.features.user.infrastructure.UserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * List Users Endpoint
 * <p>
 * REST API endpoint for listing users.
 */
@RestController
@RequestMapping("/users")
class ListUsersEndpoint {

  private final ListUsersHandler handler;
  private final UserMapper userMapper;

  public ListUsersEndpoint(ListUsersHandler handler, UserMapper userMapper) {
    this.handler = handler;
    this.userMapper = userMapper;
  }

  /**
   * List users
   */
  @GetMapping
  public ResponseEntity<List<UserResponse>> list(
      @RequestParam(required = false) UserStatus status
  ) {
    ListUsersQuery query = new ListUsersQuery(status);
    List<User> users = handler.handle(query);
    return ResponseEntity.ok(userMapper.toResponseList(users));
  }
}

package com.primetech.poc.identity.features.auth.login.chain;

import com.primetech.poc.identity.features.assignment.domain.UserRoleAssignment;
import com.primetech.poc.identity.features.auth.login.LoginCommand;
import com.primetech.poc.identity.features.role.domain.Role;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.user.domain.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable context for the login chain.
 * <p>
 * Holds all state accumulated during the login process, including:
 * - The original login command
 * - Resolved tenant entity
 * - Authenticated user
 * - Role assignments and resolved roles
 * - Generated tokens
 * <p>
 * This context is passed through each processor in the chain, allowing
 * each step to read previously set values and add new ones.
 */
public class LoginContext {

    private final LoginCommand command;

    // Step 1: Tenant validation
    private TenantEntity tenant;

    // Step 2: User authentication
    private User user;

    // Step 3: Role resolution
    private List<UserRoleAssignment> assignments = Collections.emptyList();
    private Map<UUID, Role> roleMap = Collections.emptyMap();
    private List<String> roleCodes = Collections.emptyList();
    private List<String> propertyIds = Collections.emptyList();

    // Step 4: Token generation
    private String accessToken;
    private String refreshToken;

    public LoginContext(LoginCommand command) {
        this.command = command;
    }

    public LoginCommand getCommand() {
        return command;
    }

    public TenantEntity getTenant() {
        return tenant;
    }

    public void setTenant(TenantEntity tenant) {
        this.tenant = tenant;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<UserRoleAssignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<UserRoleAssignment> assignments) {
        this.assignments = assignments != null ? new ArrayList<>(assignments) : Collections.emptyList();
    }

    public Map<UUID, Role> getRoleMap() {
        return roleMap;
    }

    public void setRoleMap(Map<UUID, Role> roleMap) {
        this.roleMap = roleMap != null ? Map.copyOf(roleMap) : Collections.emptyMap();
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes != null ? List.copyOf(roleCodes) : Collections.emptyList();
    }

    public List<String> getPropertyIds() {
        return propertyIds;
    }

    public void setPropertyIds(List<String> propertyIds) {
        this.propertyIds = propertyIds != null ? List.copyOf(propertyIds) : Collections.emptyList();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

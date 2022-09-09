// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.filter.security.misc.User;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jonmv
 */
public class MockUserManagement extends AbstractComponent implements UserManagement {

    private final Map<Role, Set<User>> memberships = new HashMap<>();

    private Set<User> get(Role role) {
        var membership = memberships.get(role);
        if (membership == null) {
            throw new IllegalArgumentException(role + " not found");
        }
        return membership;
    }

    @Override
    public void createRole(Role role) {
        if (memberships.containsKey(role))
            throw new IllegalArgumentException(role + " already exists.");

        memberships.put(role, new HashSet<>());
    }

    @Override
    public void deleteRole(Role role) {
        memberships.remove(role);
    }

    @Override
    public void addUsers(Role role, Collection<UserId> users) {
        List<User> userObjs = users.stream()
                                   .map(id -> new User(id.value(), id.value(), null, null))
                                   .toList();
        get(role).addAll(userObjs);
    }

    @Override
    public void addToRoles(UserId user, Collection<Role> roles) {
        for (Role role : roles) {
            addUsers(role, Collections.singletonList(user));
        }
    }

    @Override
    public void removeUsers(Role role, Collection<UserId> users) {
        get(role).removeIf(user -> users.contains(new UserId(user.email())));
    }

    @Override
    public void removeFromRoles(UserId user, Collection<Role> roles) {
        for (Role role : roles) {
            removeUsers(role, Collections.singletonList(user));
        }
    }

    @Override
    public List<User> listUsers(Role role) {
        return List.copyOf(get(role));
    }

    @Override
    public List<Role> listRoles(UserId userId) {
        return memberships.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(user -> user.name().equals(userId.value())))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public List<Role> listRoles() {
        return new ArrayList<>(memberships.keySet());
    }

    @Override
    public Optional<User> findUser(String email) {
        return memberships.values().stream()
                .flatMap(Collection::stream)
                .filter(user -> user.email().equals(email))
                .findFirst();
    }

    @Override
    public List<User> findUsers(String query) {
        return List.of();
    }
}

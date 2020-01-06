// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.user.User;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jonmv
 */
public class MockUserManagement implements UserManagement {

    private final Map<Role, Set<User>> memberships = new HashMap<>();

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
                                   .collect(Collectors.toList());
        memberships.get(role).addAll(userObjs);
    }

    @Override
    public void addToRoles(UserId user, Collection<Role> roles) {
        for (Role role : roles) {
            addUsers(role, Collections.singletonList(user));
        }
    }

    @Override
    public void removeUsers(Role role, Collection<UserId> users) {
        memberships.get(role).removeIf(user -> users.contains(new UserId(user.email())));
    }

    @Override
    public void removeFromRoles(UserId user, Collection<Role> roles) {
        for (Role role : roles) {
            removeUsers(role, Collections.singletonList(user));
        }
    }

    @Override
    public List<User> listUsers(Role role) {
        return List.copyOf(memberships.get(role));
    }

}

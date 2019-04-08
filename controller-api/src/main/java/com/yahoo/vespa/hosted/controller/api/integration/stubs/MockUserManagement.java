package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.user.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jonmv
 */
public class MockUserManagement implements UserManagement {

    private final Map<Role, Set<UserId>> memberships = new HashMap<>();

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
        memberships.get(role).addAll(users);
    }

    @Override
    public void removeUsers(Role role, Collection<UserId> users) {
        memberships.get(role).removeAll(users);
    }

    @Override
    public List<UserId> listUsers(Role role) {
        return List.copyOf(memberships.get(role));
    }

}

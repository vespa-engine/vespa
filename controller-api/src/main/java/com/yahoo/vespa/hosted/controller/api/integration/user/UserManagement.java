// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.jdisc.http.filter.security.misc.User;
import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Management of {@link UserId}s as members of {@link Role}s.
 *
 * @author jonmv
 */
public interface UserManagement {

    /** Creates the given role, or throws if the role already exists. */
    void createRole(Role role);

    /** Ensures the given role does not exist. */
    void deleteRole(Role role);

    /** Ensures the given users exist, and are part of the given role, or throws if the role does not exist. */
    void addUsers(Role role, Collection<UserId> users);

    /** Ensures the given user exist, and are part of the given roles, or throws if the roles does not exist. */
    void addToRoles(UserId  user, Collection<Role> roles);

    /** Ensures none of the given users are part of the given role, or throws if the role does not exist. */
    void removeUsers(Role role, Collection<UserId> users);

    /** Ensures the given users are not part of the given role, or throws if the roles does not exist. */
    void removeFromRoles(UserId  user, Collection<Role> roles);

    /** Returns all users in the given role, or throws if the role does not exist. */
    List<User> listUsers(Role role);

    /** Returns all roles of which the given user is part, or throws if the user does not exist */
    List<Role> listRoles(UserId user);

    /** Returns all roles */
    List<Role> listRoles();

    /** Find a user with all attributes */
    Optional<User> findUser(String email);

    /** Find all users from the database given query */
    List<User> findUsers(String query);
}

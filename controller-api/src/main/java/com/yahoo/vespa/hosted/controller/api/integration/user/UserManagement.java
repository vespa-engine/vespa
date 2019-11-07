package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.List;

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
    void addRoles(UserId  user, Collection<Role> roles);

    /** Ensures none of the given users are part of the given role, or throws if the role does not exist. */
    void removeUsers(Role role, Collection<UserId> users);

    /** Returns all users in the given role, or throws if the role does not exist. */
    List<User> listUsers(Role role);

}

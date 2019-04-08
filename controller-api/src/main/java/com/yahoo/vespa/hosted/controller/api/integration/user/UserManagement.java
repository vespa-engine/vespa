package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.List;

/**
 * Management of {@link UserId}s and {@link RoleId}s, used for access control with {@link Role}s.
 *
 * @author jonmv
 */
public interface UserManagement {

    /** Creates the given role, or throws if the role already exists. */
    void createRole(RoleId role);

    /** Ensures the given role does not exist. */
    void deleteRole(RoleId role);

    /** Ensures the given users exist, and are part of the given role, or throws if the role does not exist. */
    void addUsers(RoleId role, Collection<UserId> users);

    /** Ensures none of the given users are part of the given role, or throws if the role does not exist. */
    void removeUsers(RoleId role, Collection<UserId> users);

    /** Returns all known roles. */
    default List<RoleId> listRoles() { throw new UnsupportedOperationException("To be removed."); }

    /** Returns all users in the given role, or throws if the role does not exist. */
    List<UserId> listUsers(RoleId role);

}

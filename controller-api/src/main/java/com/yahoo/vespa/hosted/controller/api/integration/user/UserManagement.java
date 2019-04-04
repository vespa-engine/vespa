package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.vespa.hosted.controller.api.role.Role;

import java.util.Collection;
import java.util.List;

/**
 * Management of {@link UserId}s and {@link GroupId}s, used for access control with {@link Role}s.
 *
 * @author jonmv
 */
public interface UserManagement {

    /** Creates the given group, or throws if the group already exists. */
    void createGroup(GroupId group);

    /** Deletes the given group, or throws if it doesn't already exist.. */
    void deleteGroup(GroupId group);

    /** Ensures the given users exist, and are part of the given group, or throws if the group does not exist. */
    void addUsers(GroupId group, Collection<UserId> users);

    /** Ensures none of the given users are part of the given group, or throws if the group does not exist. */
    void removeUsers(GroupId group, Collection<UserId> users);

    /** Returns all known groups. */
    List<GroupId> listGroups();

    /** Returns all users in the given group, or throws if the group does not exist. */
    List<UserId> listUsers(GroupId gruop);

}

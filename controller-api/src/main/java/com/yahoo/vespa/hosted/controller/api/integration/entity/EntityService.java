// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.entity;

import com.yahoo.vespa.hosted.controller.api.identifiers.Property;
import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserGroup;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.util.Map;
import java.util.Set;

/**
 * A service which provides access to business-specific entities.
 *
 * @author mpolden
 */
public interface EntityService {

    /** List all properties known by the service */
    Map<PropertyId, Property> listProperties();

    /** List all groups of which user is a member */
    Set<UserGroup> getUserGroups(UserId user);

    /** Whether user is a member of the group */
    boolean isGroupMember(UserId user, UserGroup group);

}

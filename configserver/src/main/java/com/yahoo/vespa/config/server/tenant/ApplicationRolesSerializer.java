// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.ApplicationRoles;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

/**
 * @author mortent
 */
public class ApplicationRolesSerializer {

    private final static String hostRoleField = "applicationHostRole";
    private final static String containerRoleField = "applicationContainerRole";


    public static void toSlime(ApplicationRoles applicationRoles, Cursor object) {
        object.setString(hostRoleField, applicationRoles.applicationHostRole());
        object.setString(containerRoleField, applicationRoles.applicationContainerRole());
    }

    public static ApplicationRoles fromSlime(Inspector inspector) {
        return new ApplicationRoles(inspector.field(hostRoleField).asString(),
                inspector.field(containerRoleField).asString());

    }
}

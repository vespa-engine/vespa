// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;

/**
 * A {@link Role} with a {@link Context} of a {@link TenantName} and an {@link ApplicationName}.
 *
 * @author jonmv
 */
public class ApplicationRole extends Role {

    ApplicationRole(RoleDefinition roleDefinition, TenantName tenant, ApplicationName application) {
        super(roleDefinition, Context.limitedTo(tenant, application));
    }

    /** Returns the {@link TenantName} this is bound to. */
    public TenantName tenant() { return context.tenant().get(); }

    /** Returns the {@link ApplicationName} this is bound to. */
    public ApplicationName application() { return context.application().get(); }

    @Override
    public String toString() {
        return "role '" + definition() + "' of '" + application() + "' owned by '" + tenant() + "'";
    }

}

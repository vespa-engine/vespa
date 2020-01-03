// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;

/**
 * A {@link Role} with a {@link Context} of a {@link TenantName}, an {@link ApplicationName}, and an {@link InstanceName}.
 *
 * @author jonmv
 */
public class InstanceRole extends Role {

    InstanceRole(RoleDefinition roleDefinition, TenantName tenant, ApplicationName application, InstanceName instance) {
        super(roleDefinition, Context.limitedTo(tenant, application, instance));
    }

    /** Returns the {@link TenantName} this is bound to. */
    public TenantName tenant() { return context.tenant().get(); }

    /** Returns the {@link ApplicationName} this is bound to. */
    public ApplicationName application() { return context.application().get(); }

    /** Returns the {@link InstanceName} this is bound to. */
    public InstanceName instance() { return context.instance().get(); }

    @Override
    public String toString() {
        return "role '" + definition() + "' of instance '" + instance() + "' of '" + application() + "' owned by '" + tenant() + "'";
    }

}

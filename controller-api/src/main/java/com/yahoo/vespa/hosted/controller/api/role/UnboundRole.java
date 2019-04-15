package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.SystemName;

/**
 * A {@link Role} with a {@link Context} of only a {@link SystemName}.
 *
 * @author jonmv
 */
public class UnboundRole extends Role {

    UnboundRole(RoleDefinition roleDefinition) {
        super(roleDefinition, Context.unlimited());
    }

    @Override
    public String toString() {
        return "role '" + definition() + "'";
    }

}

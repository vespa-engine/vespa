// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

/**
 * A {@link Role} with an unlimited {@link Context}.
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

// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import java.util.EnumSet;
import java.util.Set;

/**
 * This declares all tenant roles known to the controller. A role contains one or more {@link Policy}'s which decide
 * what actions a member of a role can perform.
 *
 * @author mpolden
 */
public enum Role {

    hostedOperator(Policy.operator),
    tenantAdmin(Policy.tenant),
    tenantPipelineOperator(Policy.buildService),
    everyone(Policy.unauthorized);

    private final Set<Policy> policies;

    Role(Policy... policies) {
        this.policies = EnumSet.copyOf(Set.of(policies));
    }

    /**
     * Returns whether this role is allowed to perform action in given role context. Action is allowed if at least one
     * policy evaluates to true.
     */
    public boolean allows(Action action, String path, Context context) {
        return policies.stream().anyMatch(policy -> policy.evaluate(action, path, context));
    }

}


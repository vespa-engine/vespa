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

    /** Deus ex machina. */
    hostedOperator(Policy.operator),

    /** Tenant administrator with full access to all child resources. */
    tenantAdmin(Policy.manager,
                Policy.tenant,
                Policy.application,
                Policy.development), // TODO remove, as it is covered by applicationAdmin.

    /** Build and continuous delivery service. */
    tenantPipelineOperator(Policy.buildService,
                           Policy.submission,
                           Policy.production),

    /** Application administrator with full access to an already existing application. */
    applicationAdmin(Policy.tenantRead,
                     Policy.applicationModify,
                     Policy.development,
                     Policy.production),

    /** Application operator with read access to all information about an application. */
    applicationOperator(Policy.tenantRead,
                        Policy.applicationRead,
                        Policy.deploymentRead),

    /** Build service which may submit new applications for continuous deployment. */
    buildService(Policy.submission),

    /** Base role which everyone is part of. */
    everyone(Policy.classifiedRead,
             Policy.publicRead,
             Policy.onboardUser,
             Policy.onboardTenant);

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


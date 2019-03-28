// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import java.util.EnumSet;
import java.util.Set;

/**
 * This declares all tenant roles known to the controller. A role contains one or more {@link Policy}'s which decide
 * what actions a member of a role can perform, and, optionally, a "lower ranking" role from which all policies are
 * inherited. Read the list of roles from everyone to tenantAdmin, in order, to see what policies such a role includes.
 *
 * @author mpolden
 * @author jonmv
 */
public enum Role {

    /** Deus ex machina. */
    hostedOperator(Policy.operator),

    /** Build service which may submit new applications for continuous deployment. */
    buildService(Policy.submission,
                 Policy.applicationRead),

    /** Base role which every user is part of. */
    everyone(Policy.classifiedRead,
             Policy.publicRead,
             Policy.userCreate,
             Policy.tenantCreate),

    /** Application reader which can see all information about an application, its tenant and deployments. */
    applicationReader(everyone,
                      Policy.tenantRead,
                      Policy.applicationRead,
                      Policy.deploymentRead),

    /** Application developer with access to deploy to development zones. */
    applicationDeveloper(applicationReader,
                         Policy.developmentDeployment),

    /** Application operator with access to normal, operational tasks of an application. */
    applicationOperator(applicationDeveloper,
                        Policy.applicationOperations),

    /** Application administrator with full access to an already existing application, including emergency operations. */
    applicationAdmin(applicationOperator,
                     Policy.applicationUpdate,
                     Policy.productionDeployment,
                     Policy.submission),

    /** Tenant admin with full access to all tenant resources, including the ability to create new applications. */
    tenantAdmin(applicationAdmin,
                Policy.applicationCreate,
                Policy.applicationDelete,
                Policy.manager,
                Policy.tenantWrite),

    /** Build and continuous delivery service. */ // TODO replace with buildService, when everyone is on new pipeline.
    tenantPipeline(Policy.submission,
                   Policy.deploymentPipeline,
                   Policy.productionDeployment),

    /** Tenant administrator with full access to all child resources. */
    athenzTenantAdmin(Policy.tenantWrite,
                      Policy.tenantRead,
                      Policy.applicationCreate,
                      Policy.applicationUpdate,
                      Policy.applicationDelete,
                      Policy.applicationOperations,
                      Policy.developmentDeployment); // TODO remove, as it is covered by applicationAdmin.

    private final Set<Policy> policies;

    Role(Policy... policies) {
        this.policies = EnumSet.copyOf(Set.of(policies));
    }

    Role(Role inherited, Policy... policies) {
        this.policies = EnumSet.copyOf(Set.of(policies));
        this.policies.addAll(inherited.policies);
    }

    /**
     * Returns whether this role is allowed to perform action in given role context. Action is allowed if at least one
     * policy evaluates to true.
     */
    public boolean allows(Action action, String path, Context context) {
        return policies.stream().anyMatch(policy -> policy.evaluate(action, path, context));
    }

}


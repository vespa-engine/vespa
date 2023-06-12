// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * This declares all tenant roles known to the controller. A role contains one or more {@link Policy}s which decide
 * what actions a member of a role can perform, given a {@link Context} for the action.
 *
 * Optionally, some role definitions also inherit all policies from a "lower ranking" role.
 *
 * See {@link Role} for roles bound to a context, where policies can be evaluated.
 *
 * @author mpolden
 * @author jonmv
 */
public enum RoleDefinition {

    /** Deus ex machina. */
    hostedOperator(Policy.operator),

    /** Machina autem exspiravit. */
    hostedSupporter(Policy.supporter),

    /** Base role which every user is part of. */
    everyone(Policy.classifiedRead,
             Policy.publicRead,
             Policy.user,
             Policy.tenantCreate,
             Policy.emailVerification),

    /** Build service which may submit new applications for continuous deployment. */
    buildService(everyone,
                 Policy.tenantRead,
                 Policy.applicationRead,
                 Policy.deploymentRead,
                 Policy.submission),

    /** Reader — the base role for all tenant users */
    reader(Policy.tenantRead,
           Policy.applicationRead,
           Policy.deploymentRead,
           Policy.publicRead,
           Policy.paymentInstrumentRead,
           Policy.paymentInstrumentDelete,
           Policy.billingInformationRead,
           Policy.horizonProxyOperations),

    /** User — the dev.ops. role for normal Vespa tenant users */
    developer(Policy.applicationCreate,
              Policy.applicationUpdate,
              Policy.applicationDelete,
              Policy.applicationOperations,
              Policy.developmentDeployment,
              Policy.keyManagement,
              Policy.submission,
              Policy.paymentInstrumentRead,
              Policy.paymentInstrumentDelete,
              Policy.billingInformationRead,
              Policy.secretStoreOperations,
              Policy.dataplaneToken),

    /** Developer for manual deployments for a tenant */
    hostedDeveloper(Policy.developmentDeployment),

    /** Admin — the administrative function for user management etc. */
    administrator(Policy.tenantUpdate,
                  Policy.tenantManager,
                  Policy.tenantDelete,
                  Policy.tenantArchiveAccessManagement,
                  Policy.applicationManager,
                  Policy.keyRevokal,
                  Policy.paymentInstrumentRead,
                  Policy.billingInformationRead,
                  Policy.accessRequests
            ),

    /** Headless — the application specific role identified by deployment keys for production */
    headless(Policy.submission),

    /** Tenant administrator with full access to all child resources. */
    athenzTenantAdmin(everyone,
                      Policy.tenantRead,
                      Policy.tenantUpdate,
                      Policy.tenantDelete,
                      Policy.applicationCreate,
                      Policy.applicationUpdate,
                      Policy.applicationDelete,
                      Policy.applicationOperations,
                      Policy.keyManagement,
                      Policy.developmentDeployment),

    systemFlagsDeployer(Policy.systemFlagsDeploy, Policy.systemFlagsDryrun),

    systemFlagsDryrunner(Policy.systemFlagsDryrun),

    paymentProcessor(Policy.paymentProcessor),

    hostedAccountant(Policy.hostedAccountant,
                     Policy.collectionMethodUpdate,
                     Policy.planUpdate,
                     Policy.tenantUpdate);

    private final Set<RoleDefinition> parents;
    private final Set<Policy> policies;

    RoleDefinition(Policy... policies) {
        this(Set.of(), policies);
    }

    RoleDefinition(RoleDefinition parent, Policy... policies) {
        this(Set.of(parent), policies);
    }

    RoleDefinition(Set<RoleDefinition> parents, Policy... policies) {
        this.parents = new HashSet<>(parents);
        this.policies = EnumSet.copyOf(Set.of(policies));
        for (RoleDefinition parent : parents) {
            this.parents.addAll(parent.parents);
            this.policies.addAll(parent.policies);
        }
    }

    Set<Policy> policies() {
        return policies;
    }

    Set<RoleDefinition> inherited() {
        return parents;
    }

}

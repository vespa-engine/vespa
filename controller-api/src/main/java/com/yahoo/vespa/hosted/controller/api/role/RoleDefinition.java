package com.yahoo.vespa.hosted.controller.api.role;

import java.util.EnumSet;
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

    /** Application administrator with the additional ability to delete an application. */
    applicationOwner(applicationOperator,
                     Policy.applicationDelete),

    /** Tenant operator with admin access to all applications under the tenant, as well as the ability to create applications. */
    tenantOperator(applicationAdmin,
                   Policy.applicationCreate),

    /** Tenant admin with full access to all tenant resources, except deleting the tenant. */
    tenantAdmin(tenantOperator,
                Policy.applicationDelete,
                Policy.manager,
                Policy.tenantUpdate),

    /** Tenant admin with full access to all tenant resources. */
    tenantOwner(tenantAdmin,
                Policy.tenantDelete),

    /** Build and continuous delivery service. */ // TODO replace with buildService, when everyone is on new pipeline.
    tenantPipeline(everyone,
                   Policy.submission,
                   Policy.deploymentPipeline,
                   Policy.productionDeployment),

    /** Tenant administrator with full access to all child resources. */
    athenzTenantAdmin(everyone,
                      Policy.tenantRead,
                      Policy.tenantUpdate,
                      Policy.tenantDelete,
                      Policy.applicationCreate,
                      Policy.applicationUpdate,
                      Policy.applicationDelete,
                      Policy.applicationOperations,
                      Policy.developmentDeployment);

    private final Set<Policy> policies;

    RoleDefinition(Policy... policies) {
        this.policies = EnumSet.copyOf(Set.of(policies));
    }

    RoleDefinition(RoleDefinition inherited, Policy... policies) {
        this.policies = EnumSet.copyOf(Set.of(policies));
        this.policies.addAll(inherited.policies);
    }

    Set<Policy> policies() { return policies; }

}

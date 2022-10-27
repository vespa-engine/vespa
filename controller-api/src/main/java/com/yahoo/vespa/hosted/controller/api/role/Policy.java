// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.net.URI;
import java.util.Set;

/**
 * Policies for REST APIs in the controller. A policy is only considered when defined in a {@link Role}.
 *
 * - A policy describes a set of {@link Privilege}s, which are valid for a set of {@link SystemName}s.
 * - A policy is evaluated by an {@link Enforcer}, which holds the {@link SystemName} the evaluation is done in.
 * - A policy is evaluated in a {@link Context}, which may limit it to a specific {@link TenantName} or
 *   {@link ApplicationName}.
 *
 * @author mpolden
 */
enum Policy {

    /** Full access to everything. */
    operator(Privilege.grant(Action.all())
                      .on(PathGroup.allExcept(PathGroup.operatorRestrictedPaths()))
                      .in(SystemName.all()),
             Privilege.grant(Action.read)
                      .on(PathGroup.billingPathsNoToken())
                      .in(SystemName.all()),
             Privilege.grant(Action.read)
                      .on(PathGroup.billingToken)
                      .in(SystemName.PublicCd)),

    /** Full access to everything. */
    supporter(Privilege.grant(Action.read)
                       .on(PathGroup.all())
                       .in(SystemName.all())),

    /** Full access to user management for a tenant in select systems. */
    tenantManager(Privilege.grant(Action.all())
                           .on(PathGroup.tenantUsers)
                           .in(SystemName.all())),

    /** Full access to user management for an application in select systems. */
    applicationManager(Privilege.grant(Action.all())
                                .on(PathGroup.applicationUsers)
                                .in(SystemName.all())),

    /** Access to create a user tenant in select systems. */
    user(Privilege.grant(Action.create, Action.update)
                  .on(PathGroup.user)
                  .in(SystemName.main, SystemName.cd)),

    /** Access to create a tenant. */
    tenantCreate(Privilege.grant(Action.create)
                          .on(PathGroup.tenant)
                          .in(SystemName.all())),

    /** Full access to tenant information and settings. */
    tenantDelete(Privilege.grant(Action.delete)
                          .on(PathGroup.tenant)
                          .in(SystemName.all())),

    /** Full access to tenant information and settings. */
    tenantUpdate(Privilege.grant(Action.update)
                          .on(PathGroup.tenantInfo)
                          .on(PathGroup.tenant)
                          .in(SystemName.all())),

    /** Read access to tenant information and settings. */
    tenantRead(Privilege.grant(Action.read)
                        .on(PathGroup.tenant, PathGroup.tenantInfo, PathGroup.tenantUsers, PathGroup.applicationUsers)
                        .in(SystemName.all())),

    /** Access to set and unset archive access role under a tenant. */
    tenantArchiveAccessManagement(Privilege.grant(Action.update, Action.delete)
                                           .on(PathGroup.tenantArchiveAccess)
                                           .in(SystemName.all())),

    /** Access to create application under a certain tenant. */
    applicationCreate(Privilege.grant(Action.create)
                               .on(PathGroup.application)
                               .in(SystemName.all())),

    /** Read access to application information and settings. */
    applicationRead(Privilege.grant(Action.read)
                             .on(PathGroup.application, PathGroup.applicationInfo, PathGroup.reindexing, PathGroup.serviceDump)
                             .in(SystemName.all())),

    /** Update access to application information and settings. */
    applicationUpdate(Privilege.grant(Action.update)
                               .on(PathGroup.application, PathGroup.applicationInfo)
                               .in(SystemName.all())),

    /** Access to delete a certain application. */
    applicationDelete(Privilege.grant(Action.delete)
                               .on(PathGroup.application)
                               .in(SystemName.all())),

    /** Full access to application information and settings. */
    applicationOperations(Privilege.grant(Action.write())
                                   .on(PathGroup.applicationInfo, PathGroup.productionRestart, PathGroup.reindexing, PathGroup.serviceDump)
                                   .in(SystemName.all())),

    /** Access to create and delete developer and deploy keys under a tenant. */
    keyManagement(Privilege.grant(Action.write())
                           .on(PathGroup.tenantKeys, PathGroup.applicationKeys)
                           .in(SystemName.all())),

    /** Access to revoke keys from the tenant */
    keyRevokal(Privilege.grant(Action.delete)
                        .on(PathGroup.tenantKeys, PathGroup.applicationKeys)
                        .in(SystemName.all())),

    /** Full access to application development deployments. */
    developmentDeployment(Privilege.grant(Action.all())
                                   .on(PathGroup.developmentDeployment, PathGroup.developmentRestart)
                                   .in(SystemName.all())),

    /** Read access to all application deployments. */
    deploymentRead(Privilege.grant(Action.read)
                            .on(PathGroup.developmentDeployment, PathGroup.productionDeployment)
                            .in(SystemName.all())),

    /** Full access to submissions for continuous deployment. */
    submission(Privilege.grant(Action.all())
                        .on(PathGroup.submission)
                        .in(SystemName.all())),

    /** Read access to all information in select systems. */
    classifiedRead(Privilege.grant(Action.read)
                            .on(PathGroup.allExcept(PathGroup.classifiedOperator))
                            .in(SystemName.main, SystemName.cd)),

    /** Read access to public info. */
    publicRead(Privilege.grant(Action.read)
                        .on(PathGroup.publicInfo)
                        .in(SystemName.all())),

    /** Access to /system-flags/v1/deploy. */
    systemFlagsDeploy(Privilege.grant(Action.update)
                                  .on(PathGroup.systemFlagsDeploy)
                                  .in(SystemName.all())),

    /** Access to /system-flags/v1/dryrun. */
    systemFlagsDryrun(Privilege.grant(Action.update)
                                  .on(PathGroup.systemFlagsDryrun)
                                  .in(SystemName.all())),

    /** Access to /payment/notification */
    paymentProcessor(Privilege.grant(Action.create)
                                .on(PathGroup.paymentProcessor)
                                .in(SystemName.PublicCd)),

    /** Read your own instrument information */
    paymentInstrumentRead(Privilege.grant(Action.read)
                                   .on(PathGroup.billingInstrument)
                                   .in(SystemName.PublicCd, SystemName.Public)),

    /** Ability to update tenant payment instrument */
    paymentInstrumentUpdate(Privilege.grant(Action.update)
                                     .on(PathGroup.billingInstrument)
                                     .in(SystemName.PublicCd, SystemName.Public)),

    /** Ability to remove your own payment instrument */
    paymentInstrumentDelete(Privilege.grant(Action.delete)
                                     .on(PathGroup.billingInstrument)
                                     .in(SystemName.PublicCd, SystemName.Public)),

    /** Get the token to view instrument form */
    paymentInstrumentCreate(Privilege.grant(Action.read)
                                    .on(PathGroup.billingToken)
                                    .in(SystemName.PublicCd, SystemName.Public)),

    /** Ability to update tenant payment instrument */
    planUpdate(Privilege.grant(Action.update)
            .on(PathGroup.billingPlan, PathGroup.billing)
            .in(SystemName.PublicCd, SystemName.Public)),

    /** Ability to update tenant collection method */
    collectionMethodUpdate(Privilege.grant(Action.update)
            .on(PathGroup.billingCollection)
            .in(SystemName.PublicCd, SystemName.Public)),


    /** Read the generated bills */
    billingInformationRead(Privilege.grant(Action.read)
                                    .on(PathGroup.billingList, PathGroup.billing)
                                    .in(SystemName.PublicCd, SystemName.Public)),

    accessRequests(Privilege.grant(Action.all())
            .on(PathGroup.accessRequests, PathGroup.accessRequestApproval)
            .in(SystemName.PublicCd, SystemName.Public)),

    /** Invoice management */
    hostedAccountant(Privilege.grant(Action.all())
                                    .on(PathGroup.hostedAccountant, PathGroup.accountant, PathGroup.userSearch)
                                    .in(SystemName.PublicCd, SystemName.Public)),

    /** Listing endpoint certificates and re-requesting certificates */
    endpointCertificateApi(Privilege.grant(Action.all())
            .on(PathGroup.endpointCertificates)
            .in(SystemName.all())),

    /** Secret store operations */
    secretStoreOperations(Privilege.grant(Action.all())
                                .on(PathGroup.secretStore)
                                .in(SystemName.PublicCd, SystemName.Public)),

    horizonProxyOperations(Privilege.grant(Action.all())
            .on(PathGroup.horizonProxy)
            .in(SystemName.PublicCd, SystemName.Public)),

    emailVerification(Privilege.grant(Action.create)
                        .on(PathGroup.emailVerification)
                        .in(SystemName.PublicCd, SystemName.Public));

    private final Set<Privilege> privileges;

    Policy(Privilege... privileges) {
        this.privileges = Set.of(privileges);
    }

    /** Returns whether action is allowed on path in given context */
    boolean evaluate(Action action, URI uri, Context context, SystemName system) {
        return privileges.stream().anyMatch(privilege -> privilege.actions().contains(action) &&
                                                         privilege.systems().contains(system) &&
                                                         privilege.pathGroups().stream()
                                                                  .anyMatch(pg -> pg.matches(uri, context)));
    }

}

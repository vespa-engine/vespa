// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

import java.net.URI;
import java.util.Set;

/**
 * Policies for REST APIs in the controller. A policy is only considered when defined in a {@link Role}.
 * A policy describes a set of {@link Privilege}s, which are valid for a set of {@link SystemName}s.
 * A policy is evaluated with a {@link Context}, which provides the {@link SystemName} the policy is
 * evaluated in, and any limitations to a specific {@link TenantName} or {@link ApplicationName}.
 *
 * @author mpolden
 */
public enum Policy {

    /** Full access to everything. */
    operator(Privilege.grant(Action.all())
                      .on(PathGroup.all())
                      .in(SystemName.all())),

    /** Full access to user management in select systems. */
    manager(Privilege.grant(Action.all())
                     .on(PathGroup.userManagement)
                     .in(SystemName.Public)),

    /** Access to create a user tenant in select systems. */
    userCreate(Privilege.grant(Action.update)
                        .on(PathGroup.user)
                        .in(SystemName.main, SystemName.cd, SystemName.dev)),

    /** Access to create a tenant in select systems. */
    tenantCreate(Privilege.grant(Action.create)
                          .on(PathGroup.tenant)
                          .in(SystemName.main, SystemName.cd, SystemName.dev)), // TODO SystemName.all()

    /** Full access to tenant information and settings. */
    tenantWrite(Privilege.grant(Action.write())
                         .on(PathGroup.tenant)
                         .in(SystemName.all())),

    /** Read access to tenant information and settings. */
    tenantRead(Privilege.grant(Action.read)
                        .on(PathGroup.tenant, PathGroup.tenantInfo)
                        .in(SystemName.all())),

    /** Access to create application under a certain tenant. */
    applicationCreate(Privilege.grant(Action.create)
                               .on(PathGroup.application)
                               .in(SystemName.all())),

    /** Read access to application information and settings. */
    applicationRead(Privilege.grant(Action.read)
                             .on(PathGroup.application, PathGroup.applicationInfo)
                             .in(SystemName.all())),

    /** Read access to application information and settings. */
    applicationUpdate(Privilege.grant(Action.update)
                               .on(PathGroup.application, PathGroup.applicationInfo)
                               .in(SystemName.all())),

    /** Access to delete a certain application. */
    applicationDelete(Privilege.grant(Action.delete)
                               .on(PathGroup.application)
                               .in(SystemName.all())),

    /** Full access to application information and settings. */
    applicationOperations(Privilege.grant(Action.write())
                                   .on(PathGroup.applicationInfo, PathGroup.applicationRestart)
                                   .in(SystemName.all())),

    /** Full access to application development deployments. */
    developmentDeployment(Privilege.grant(Action.all())
                                   .on(PathGroup.developmentDeployment)
                                   .in(SystemName.all())),

    /** Full access to application production deployments. */
    productionDeployment(Privilege.grant(Action.all())
                                  .on(PathGroup.productionDeployment)
                                  .in(SystemName.all())),

    /** Read access to all application deployments. */
    deploymentRead(Privilege.grant(Action.read)
                            .on(PathGroup.developmentDeployment, PathGroup.productionDeployment)
                            .in(SystemName.all())),

    /** Full access to submissions for continuous deployment. */
    submission(Privilege.grant(Action.all())
                        .on(PathGroup.submission)
                        .in(SystemName.all())),

    /** Full access to the additional tasks needed for continuous deployment. */
    deploymentPipeline(Privilege.grant(Action.all()) // TODO remove when everyone is on new pipeline.
                                .on(PathGroup.buildService, PathGroup.applicationRestart)
                                .in(SystemName.all())),

    /** Read access to all information in select systems. */
    classifiedRead(Privilege.grant(Action.read)
                            .on(PathGroup.all())
                            .in(SystemName.main, SystemName.cd, SystemName.dev)),

    /** Read access to public info. */
    publicRead(Privilege.grant(Action.read)
                        .on(PathGroup.publicInfo)
                        .in(SystemName.all()));

    private final Set<Privilege> privileges;

    Policy(Privilege... privileges) {
        this.privileges = Set.of(privileges);
    }

    /** Returns whether action is allowed on path in given context */
    public boolean evaluate(Action action, URI uri, Context context) {
        return privileges.stream().anyMatch(privilege -> privilege.actions().contains(action) &&
                                                         privilege.systems().contains(context.system()) &&
                                                         privilege.pathGroups().stream()
                                                                  .anyMatch(pg -> pg.matches(uri, context)));
    }

}

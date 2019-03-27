// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.role;

import com.yahoo.config.provision.SystemName;

import java.util.Set;

/**
 * Policies for REST APIs in the controller. A policy is only considered when defined in a {@link Role}.
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
    onboardUser(Privilege.grant(Action.update)
                         .on(PathGroup.onboardingUser)
                         .in(SystemName.main, SystemName.cd, SystemName.dev)),

    /** Access to create a user tenant in select systems. */
    onboardTenant(Privilege.grant(Action.create)
                           .on(PathGroup.onboarding)
                           .in(SystemName.main, SystemName.cd, SystemName.dev)), // TODO SystemName.all()

    /** Full access to tenant information and settings. */
    tenant(Privilege.grant(Action.all())
                    .on(PathGroup.tenant)
                    .in(SystemName.all())),

    /** Read access to tenant information and settings. */
    tenantRead(Privilege.grant(Action.read)
                        .on(PathGroup.tenant)
                        .in(SystemName.all())),

    /** Full access to application information, settings and jobs. */
    application(Privilege.grant(Action.all())
                         .on(PathGroup.application)
                         .in(SystemName.all())),

    /** Full access to application information, settings and jobs. */
    applicationModify(Privilege.grant(Action.update)
                               .on(PathGroup.application)
                               .in(SystemName.all())),

    /** Read access to application information and settings. */
    applicationRead(Privilege.grant(Action.read)
                             .on(PathGroup.application)
                             .in(SystemName.all())),

    /** Full access to application development deployments. */
    development(Privilege.grant(Action.all())
                         .on(PathGroup.development)
                         .in(SystemName.all())),

    /** Full access to application production deployments. */
    production(Privilege.grant(Action.all())
                        .on(PathGroup.deployment)
                        .in(SystemName.all())),

    /** Read access to allapplication deployments. */
    deploymentRead(Privilege.grant(Action.read)
                            .on(PathGroup.development, PathGroup.deployment)
                            .in(SystemName.all())),

    /** Full access to submissions for continuous deployment. */
    submission(Privilege.grant(Action.all())
                        .on(PathGroup.submission)
                        .in(SystemName.all())),

    /** Full access to the additional tasks needed for continuous deployment. */
    buildService(Privilege.grant(Action.all())
                          .on(PathGroup.buildService)
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
    public boolean evaluate(Action action, String path, Context context) {
        return privileges.stream().anyMatch(privilege -> privilege.actions().contains(action) &&
                                                         privilege.systems().contains(context.system()) &&
                                                         privilege.pathGroups().stream()
                                                                  .anyMatch(pg -> pg.matches(path, context)));
    }

}

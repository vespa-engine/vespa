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

    /** Operator policy allows access to everything in all systems */
    operator(Privilege.grant(Action.all())
                      .on(PathGroup.all())
                      .in(SystemName.all())),

    /**
     * Tenant policy allows tenants to access their own tenant, in all systems, and allows global read access in
     * selected systems
     */
    tenant(Privilege.grant(Action.all())
                    .on(PathGroup.tenant)
                    .in(SystemName.all()),
           Privilege.grant(Action.read)
                    .on(PathGroup.all())
                    .in(SystemName.main, SystemName.cd, SystemName.dev)),

    /** Build service policy only allows access relevant for build service(s) */
    buildService(Privilege.grant(Action.all())
                          .on(PathGroup.buildService)
                          .in(SystemName.all())),

    /** Unauthorized policy allows creation of tenants and read of everything in selected systems */
    unauthorized(Privilege.grant(Action.update)
                          .on(PathGroup.onboardingUser)
                          .in(SystemName.main, SystemName.cd, SystemName.dev),
                 Privilege.grant(Action.create)
                          .on(PathGroup.onboardingTenant)
                          .in(SystemName.main, SystemName.cd, SystemName.dev),
                 Privilege.grant(Action.read)
                          .on(PathGroup.all())
                          .in(SystemName.main, SystemName.cd, SystemName.dev),
                 Privilege.grant(Action.read)
                          .on(PathGroup.deploymentStatus)
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

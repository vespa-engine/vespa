// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.SystemName;

import java.net.URI;

/**
 * Checks whether {@link Role}s have the required {@link Privilege}s to perform {@link Action}s on given {@link java.net.URI}s.
 *
 * @author jonmv
 */
public class Enforcer {

    private final SystemName system;

    public Enforcer(SystemName system) {
        this.system = system;
    }

    /** Returns whether {@code role} has permission to perform {@code action} on {@code resource}, in this enforcer's system. */
    public boolean allows(Role role, Action action, URI resource) {
        return role.definition().policies().stream().anyMatch(policy -> policy.evaluate(action, resource, role.context, system));
    }

}

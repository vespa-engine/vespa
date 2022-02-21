// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import com.yahoo.config.provision.SystemName;

import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Checks whether {@link Role}s have the required {@link Privilege}s to perform {@link Action}s on given {@link java.net.URI}s.
 *
 * @author jonmv
 */
public class Enforcer {

    private static final Logger logger = Logger.getLogger(Enforcer.class.getName());

    private final SystemName system;
    public Enforcer(SystemName system) {
        this.system = system;
    }

    /** Returns whether {@code role} has permission to perform {@code action} on {@code resource}, in this enforcer's system. */
    public boolean allows(Role role, Action action, URI resource) {
        List<Policy> matchingPolicies = role.definition().policies().stream()
                .filter(policy -> policy.evaluate(action, resource, role.context, system))
                .collect(Collectors.toList());
        logger.log(Level.FINE, "Matching policies for " +
                               "role: " + role.definition().name() + ", "+
                               "action " + action.name() + ", " +
                               resource.getPath() + " : " +
                               matchingPolicies.stream().map(Enum::name).collect(Collectors.joining()));
        return !matchingPolicies.isEmpty();
    }

}

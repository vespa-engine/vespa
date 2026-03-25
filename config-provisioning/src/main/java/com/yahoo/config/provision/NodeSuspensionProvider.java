// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Set;

/**
 * Provides the set of hostnames that are currently suspended for a given application.
 * Implemented in hosted Vespa using the node repository orchestrator.
 *
 * @author bjormel
 */
public interface NodeSuspensionProvider {

    NodeSuspensionProvider EMPTY = __ -> Set.of();

    /** Returns the hostnames of all nodes that are currently suspended for the given application. Never null; returns an empty set if none are suspended. */
    Set<String> suspendedHosts(ApplicationId applicationId);

}

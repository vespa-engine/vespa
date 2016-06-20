// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction for communicating with Orchestrator.
 *
 * @author bakksjo
 */
public interface Orchestrator {
    /**
     * Invokes orchestrator suspend of a host. Returns whether suspend was granted.
     */
    boolean suspend(HostName hostName);

    /**
     * Invokes orchestrator resume of a host. Returns whether resume was granted.
     */
    boolean resume(HostName hostName);

    /**
     * Invokes orchestrator suspend hosts. Returns failure reasons when failing.
     */
    Optional<String> suspend(String parentHostName, List<String> hostNames);

    /**
     * Invokes orchestrator resume of hosts. Returns failure reasons when failing.
     */
    Optional<String> resume(List<String> hostName);
}

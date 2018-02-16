// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

import java.util.List;

/**
 * Abstraction for communicating with Orchestrator.
 *
 * @author bakksjo
 */
public interface Orchestrator {
    /**
     * Invokes orchestrator suspend of a host.
     * @throws OrchestratorException if suspend was denied.
     * @throws OrchestratorNotFoundException if host is unknown to the orchestrator
     */
    void suspend(String hostName);

    /**
     * Invokes orchestrator resume of a host.
     * @throws OrchestratorException if resume was denied
     * @throws OrchestratorNotFoundException if host is unknown to the orchestrator
     */
    void resume(String hostName);

    /**
     * Invokes orchestrator suspend hosts.
     * @throws OrchestratorException if batch suspend was denied.
     */
    void suspend(String parentHostName, List<String> hostNames);
}

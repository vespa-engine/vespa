// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

/**
 * Interface for verifying the health of the node.
 *
 * @author hakonhall
 */
public interface HealthChecker extends AutoCloseable {
    /** Verify the health of an active node, just before updating the node repo and calling Orchestrator resume. */
    void verifyHealth(NodeAgentContext context);

    @Override
    void close();
}

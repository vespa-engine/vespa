// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.time.Duration;
import java.time.Instant;

/**
 * @author freva
 */
public interface NodeAgentScheduler {

    /** Schedule a tick for NodeAgent to run with the given NodeAgentContext, at no earlier than given instant */
     void scheduleTickWith(NodeAgentContext context, Instant at);

    /**
     * Will eventually freeze/unfreeze the node agent
     * @param frozen whether node agent should be frozen
     * @param timeout maximum duration this method should block while waiting for NodeAgent to reach target state
     * @return True if node agent has converged to the desired state
     */
    boolean setFrozen(boolean frozen, Duration timeout);

    /** @return the last scheduled context or a default value */
    NodeAgentContext currentContext();
}

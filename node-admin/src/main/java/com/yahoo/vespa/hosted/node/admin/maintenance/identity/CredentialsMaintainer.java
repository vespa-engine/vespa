// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.identity;

import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;

import java.time.Duration;

/**
 * A maintainer that is responsible for providing and refreshing credentials for a container.
 *
 * @author freva
 */
public interface CredentialsMaintainer {

    /**
     * Creates/refreshes credentials for the given NodeAgentContext. Called for every NodeAgent tick.
     * @return false if already converged, i.e. was a no-op.
     */
    boolean converge(NodeAgentContext context);

    /** Remove any existing credentials. This method is called just before container data is archived. */
    void clearCredentials(NodeAgentContext context);

    /** Get time until the certificate expires. Invoked each time metrics are collected.  */
    Duration certificateLifetime(NodeAgentContext context);

    /** Name used when reporting metrics */
    String name();
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.model.NodeGroup;

/**
 * @author bakksjo
 */
public class HostStateChangeDeniedException extends OrchestrationException {
    private final String constraintName;

    public HostStateChangeDeniedException(HostName hostName, String constraintName, String message) {
        this(hostName, constraintName, message, null);
    }

    public HostStateChangeDeniedException(HostName hostName, String constraintName, String message, Exception e) {
        this(hostName.s(), constraintName, message, e);
    }

    public HostStateChangeDeniedException(NodeGroup nodeGroup, String constraintName, String message) {
        this(nodeGroup.toCommaSeparatedString(), constraintName, message, null);
    }

    private HostStateChangeDeniedException(String nodes,
                                           String constraintName,
                                           String message,
                                           Throwable cause) {
        super(createMessage(nodes, constraintName, message), cause);
        this.constraintName = constraintName;
    }

    private static String createMessage(String nodes,
                                        String constraintName,
                                        String message) {
        return "Changing the state of " + nodes + " would violate " + constraintName
                + ": " + message;
    }

    public String getConstraintName() {
        return constraintName;
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;

@SuppressWarnings("serial")
public class OrchestratorException extends ConvergenceException {
    public OrchestratorException(String message) {
        super(message);
    }
}

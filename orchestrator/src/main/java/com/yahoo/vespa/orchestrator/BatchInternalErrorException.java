// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.model.NodeGroup;

public class BatchInternalErrorException extends OrchestrationException {

    public BatchInternalErrorException(HostName parentHostname,
                                       NodeGroup nodeGroup,
                                       RuntimeException e) {
        super("Failed to suspend " + nodeGroup + " with parent host "
                + parentHostname + ": " + e.getMessage(), e);
    }

}

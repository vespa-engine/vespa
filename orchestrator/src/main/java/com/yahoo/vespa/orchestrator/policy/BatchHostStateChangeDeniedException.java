// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.OrchestrationException;

public class BatchHostStateChangeDeniedException extends OrchestrationException {

    public BatchHostStateChangeDeniedException(HostName parentHostname,
                                               NodeGroup group,
                                               HostStateChangeDeniedException e) {
        super("Failed to suspend " + group + " with parent host "
                + parentHostname + ": " + e.getMessage(), e);

    }

}

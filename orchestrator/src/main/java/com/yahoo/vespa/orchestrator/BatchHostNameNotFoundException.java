// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.List;

public class BatchHostNameNotFoundException extends OrchestrationException {

    public BatchHostNameNotFoundException(HostName parentHostname,
                                          List<HostName> hostNames,
                                          HostNameNotFoundException e) {
        super("Failed to suspend " + hostNames + " with parent host " + parentHostname + ": " + e.getMessage(), e);
    }

}

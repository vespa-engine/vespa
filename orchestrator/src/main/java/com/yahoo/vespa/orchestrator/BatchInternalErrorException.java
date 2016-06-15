// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.List;

public class BatchInternalErrorException extends OrchestrationException {
    public BatchInternalErrorException(HostName parentHostname,
                                       List<HostName> orderedHostNames,
                                       RuntimeException e) {
        super("Failed to suspend " + orderedHostNames + " with parent host "
                + parentHostname + ": " + e.getMessage(), e);
    }
}

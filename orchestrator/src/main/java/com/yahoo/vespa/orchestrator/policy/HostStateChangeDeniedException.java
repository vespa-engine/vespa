// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.model.NodeGroup;

import java.util.Optional;

/**
 * @author bakksjo
 */
public class HostStateChangeDeniedException extends OrchestrationException {
    private final String constraintName;
    private final Optional<ServiceType> serviceType;

    public HostStateChangeDeniedException(HostName hostName, String constraintName, String message) {
        this(hostName, constraintName, message, null);
    }

    public HostStateChangeDeniedException(HostName hostName, String constraintName, String message, Exception e) {
        this(hostName.s(), constraintName, Optional.empty(), message, e);
    }

    public HostStateChangeDeniedException(NodeGroup nodeGroup, String constraintName, String message) {
        this(nodeGroup.toCommaSeparatedString(), constraintName, Optional.empty(), message, null);
    }

    private HostStateChangeDeniedException(String nodes,
                                           String constraintName,
                                           Optional<ServiceType> serviceType,
                                           String message,
                                           Throwable cause) {
        super(createMessage(nodes, constraintName, serviceType, message), cause);
        this.constraintName = constraintName;
        this.serviceType = serviceType;
    }

    private static String createMessage(String nodes,
                                        String constraintName,
                                        Optional<ServiceType> serviceType,
                                        String message) {
        return "Changing the state of " + nodes + " would violate " + constraintName
                + (serviceType.isPresent() ? " for service type " + serviceType.get() : "")
                + ": " + message;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public Optional<ServiceType> getServiceType() {
        return serviceType;
    }
}

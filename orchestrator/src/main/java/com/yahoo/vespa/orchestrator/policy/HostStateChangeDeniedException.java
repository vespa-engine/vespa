// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.OrchestrationException;

/**
 * @author bakksjo
 */
public class HostStateChangeDeniedException extends OrchestrationException {

    private final String constraintName;
    private final ServiceType serviceType;

    public HostStateChangeDeniedException(HostName hostName, String constraintName, 
                                          ServiceType serviceType, String message) {
        this(hostName, constraintName, serviceType, message, null);
    }

    public HostStateChangeDeniedException(HostName hostName, String constraintName, 
                                          ServiceType serviceType, String message, Throwable cause) {
        this(hostName.toString(), constraintName, serviceType, message, cause);
    }

    public HostStateChangeDeniedException(NodeGroup nodeGroup,
                                          String constraintName,
                                          ServiceType serviceType,
                                          String message) {
        this(nodeGroup.toCommaSeparatedString(), constraintName, serviceType, message, null);
    }

    private HostStateChangeDeniedException(String nodes,
                                           String constraintName,
                                           ServiceType serviceType,
                                           String message,
                                           Throwable cause) {
        super(createMessage(nodes, constraintName, serviceType, message), cause);
        this.constraintName = constraintName;
        this.serviceType = serviceType;
    }

    private static String createMessage(String nodes,
                                        String constraintName,
                                        ServiceType serviceType,
                                        String message) {
        return "Changing the state of " + nodes + " would violate " + constraintName
                + " for service type " + serviceType + ": " + message;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

}

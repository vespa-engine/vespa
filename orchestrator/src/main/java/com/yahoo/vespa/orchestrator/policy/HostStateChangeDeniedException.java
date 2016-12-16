// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.OrchestrationException;

/**
 * @author bakksjo
 */
public class HostStateChangeDeniedException extends OrchestrationException {

    private final String constraintName;
    private final ServiceType serviceType;

    public HostStateChangeDeniedException(HostName hostName, String constraintName, 
                                          ServiceType serviceType, String message) {
        super(createMessage(hostName, constraintName, serviceType, message));
        this.constraintName = constraintName;
        this.serviceType = serviceType;
    }

    public HostStateChangeDeniedException(HostName hostName, String constraintName, 
                                          ServiceType serviceType, String message, Throwable cause) {
        super(createMessage(hostName, constraintName, serviceType, message), cause);
        this.constraintName = constraintName;
        this.serviceType = serviceType;
    }

    private static String createMessage(HostName hostName, String constraintName, 
                                        ServiceType serviceType, String message) {
        return "Changing the state of host " + hostName + " would violate " + constraintName
                + " for service type " + serviceType + ": " + message;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

}

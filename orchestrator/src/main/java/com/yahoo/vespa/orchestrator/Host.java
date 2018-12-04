// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.List;

public class Host {

    private final HostName hostName;
    private final HostStatus hostStatus;
    private final ApplicationInstanceReference applicationInstanceReference;
    private final List<ServiceInstance> serviceInstances;

    public Host(HostName hostName,
                HostStatus hostStatus,
                ApplicationInstanceReference applicationInstanceReference,
                List<ServiceInstance> serviceInstances) {
        this.hostName = hostName;
        this.hostStatus = hostStatus;
        this.applicationInstanceReference = applicationInstanceReference;
        this.serviceInstances = serviceInstances;
    }

    public HostName getHostName() {
        return hostName;
    }

    public HostStatus getHostStatus() {
        return hostStatus;
    }

    public ApplicationInstanceReference getApplicationInstanceReference() {
        return applicationInstanceReference;
    }

    public List<ServiceInstance> getServiceInstances() {
        return serviceInstances;
    }

}

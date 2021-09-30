// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.status.HostInfo;

import java.util.List;

public class Host {

    private final HostName hostName;
    private final HostInfo hostInfo;
    private final ApplicationInstanceReference applicationInstanceReference;
    private final List<ServiceInstance> serviceInstances;

    public Host(HostName hostName,
                HostInfo hostInfo,
                ApplicationInstanceReference applicationInstanceReference,
                List<ServiceInstance> serviceInstances) {
        this.hostName = hostName;
        this.hostInfo = hostInfo;
        this.applicationInstanceReference = applicationInstanceReference;
        this.serviceInstances = serviceInstances;
    }

    public HostName getHostName() {
        return hostName;
    }

    public HostInfo getHostInfo() {
        return hostInfo;
    }

    public ApplicationInstanceReference getApplicationInstanceReference() {
        return applicationInstanceReference;
    }

    public List<ServiceInstance> getServiceInstances() {
        return serviceInstances;
    }

}

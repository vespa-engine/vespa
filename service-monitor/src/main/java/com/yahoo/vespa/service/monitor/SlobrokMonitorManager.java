// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.List;

public interface SlobrokMonitorManager {
    /**
     * Get all Slobrok entries that has a name matching pattern as described in
     * Mirror::lookup.
     */
    List<Mirror.Entry> lookup(ApplicationId application, String pattern);

    /**
     * Query the ServiceMonitorStatus of a particular service.
     */
    ServiceStatus getStatus(ApplicationId applicationId,
                            ServiceType serviceType,
                            ConfigId configId);
}

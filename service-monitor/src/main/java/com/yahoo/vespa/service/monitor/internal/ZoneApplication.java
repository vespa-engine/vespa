// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.Objects;

/**
 * @author hakon
 */
public class ZoneApplication {
    private ZoneApplication() {}

    static final ApplicationId ZONE_APPLICATION_ID =
            ApplicationId.from("hosted-vespa", "routing", "default");

    static boolean isNodeAdminService(ApplicationId applicationId,
                                      ClusterId clusterId,
                                      ServiceType serviceType) {
        return Objects.equals(applicationId, ZONE_APPLICATION_ID) &&
                Objects.equals(serviceType, ServiceType.CONTAINER) &&
                Objects.equals(clusterId, ClusterId.NODE_ADMIN);
    }
}

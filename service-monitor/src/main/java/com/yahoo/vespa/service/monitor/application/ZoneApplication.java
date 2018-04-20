// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.Objects;

/**
 * @author hakon
 *
 * TODO: This does not extend HostedVespaApplication because
 * 1) It is not deployed same as the other HostedVespaApplications
 * 2) ZoneApplication has multiple clusters
 */
public class ZoneApplication {
    private ZoneApplication() {}

    public static final ApplicationId ZONE_APPLICATION_ID = HostedVespaApplication
            .createHostedVespaApplicationId("routing");

    public static boolean isNodeAdminService(ApplicationId applicationId,
                                      ClusterId clusterId,
                                      ServiceType serviceType) {
        return Objects.equals(applicationId, ZONE_APPLICATION_ID) &&
                Objects.equals(serviceType, ServiceType.CONTAINER) &&
                Objects.equals(clusterId, ClusterId.NODE_ADMIN);
    }
}

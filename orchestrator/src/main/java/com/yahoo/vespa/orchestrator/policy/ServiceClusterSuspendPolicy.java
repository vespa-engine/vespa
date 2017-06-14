// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;

/**
 * @author hakonhall
 * @author bakksjo
 */
public final class ServiceClusterSuspendPolicy {

    private static final int SUSPENSION_ALLOW_MINIMAL = 0;
    private static final int SUSPENSION_ALLOW_TEN_PERCENT = 10;
    private static final int SUSPENSION_ALLOW_ALL = 100;

    private ServiceClusterSuspendPolicy() {} // Disallow instantiation.

    public static int getSuspendPercentageAllowed(ServiceCluster<?> serviceCluster) {
        if (VespaModelUtil.ADMIN_CLUSTER_ID.equals(serviceCluster.clusterId())) {
            if (VespaModelUtil.SLOBROK_SERVICE_TYPE.equals(serviceCluster.serviceType())) {
                return SUSPENSION_ALLOW_MINIMAL;
            }

            return SUSPENSION_ALLOW_ALL;
        }

        if (VespaModelUtil.isStorage(serviceCluster)) {
            return SUSPENSION_ALLOW_MINIMAL;
        }

        return SUSPENSION_ALLOW_TEN_PERCENT;
    }

}

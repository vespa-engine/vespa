// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * @author hakonhall
 */
public interface ServiceStatusProvider {

    /**
     * Get the {@link ServiceStatus} of a particular service.
     *
     * <p>{@link ServiceStatus#NOT_CHECKED NOT_CHECKED} must be returned if the
     * service status provider does not monitor the service status for
     * the particular application, cluster, service type, and config id.
     */
    ServiceStatusInfo getStatus(ApplicationId applicationId,
                                ClusterId clusterId,
                                ServiceType serviceType,
                                ConfigId configId);

}

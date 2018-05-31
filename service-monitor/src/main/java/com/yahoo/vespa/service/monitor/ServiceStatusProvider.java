// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;

/**
 * @author hakon
 */
public interface ServiceStatusProvider {
    /** Get the {@link ServiceStatus} of a particular service. */
    ServiceStatus getStatus(ApplicationId applicationId,
                            ClusterId clusterId,
                            ServiceType serviceType,
                            ConfigId configId);
}

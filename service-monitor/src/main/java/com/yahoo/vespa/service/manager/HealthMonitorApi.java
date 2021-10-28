// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.manager;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.health.HealthMonitorManager;
import com.yahoo.vespa.service.monitor.ServiceId;
import com.yahoo.vespa.service.monitor.ServiceStatusProvider;

import java.util.List;
import java.util.Map;

/**
 * The API of {@link HealthMonitorManager} which is exported to other bundles (typically for REST).
 *
 * @author hakonhall
 */
public interface HealthMonitorApi extends ServiceStatusProvider {
    List<ApplicationId> getMonitoredApplicationIds();
    Map<ServiceId, ServiceStatusInfo> getServices(ApplicationId applicationId);
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.service.monitor.ServiceId;

/**
 * An endpoint 1-1 with a service and that can be health monitored.
 *
 * @author hakon
 */
interface HealthEndpoint {
    ServiceId getServiceId();
    String description();
    HealthMonitor startMonitoring();
}

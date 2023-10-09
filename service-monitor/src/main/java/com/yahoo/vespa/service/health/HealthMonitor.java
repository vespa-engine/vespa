// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;

/**
 * @author hakonhall
 */
interface HealthMonitor extends AutoCloseable {
    ServiceStatusInfo getStatus();

    @Override
    void close();
}

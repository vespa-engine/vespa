// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatus;

/**
 * @author hakonhall
 */
interface HealthMonitor extends AutoCloseable {
    ServiceStatus getStatus();

    @Override
    void close();
}

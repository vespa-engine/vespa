// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.health;

import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.service.executor.Runlet;

/**
 * A {@link HealthUpdater} will probe the health with {@link #run()}, whose result can be fetched with the
 * thread-safe method {@link #getServiceStatusInfo()}.
 *
 * @author hakonhall
 */
interface HealthUpdater extends Runlet {
    ServiceStatusInfo getServiceStatusInfo();
}

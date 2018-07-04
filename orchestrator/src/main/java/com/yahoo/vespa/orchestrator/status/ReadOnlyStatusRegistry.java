// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.HostName;

/**
 * Read-only view of statuses for the application instance and its hosts.
 *
 * @author oyving
 * @author Tony Vaagenes
 * @author bakksjo
 */
public interface ReadOnlyStatusRegistry {

    /**
     * Gets the current state for the given host.
     */
    HostStatus getHostStatus(HostName hostName);

    /**
     * Gets the current status for the application instance.
     */
    ApplicationInstanceStatus getApplicationInstanceStatus();

}

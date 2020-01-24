// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Set;

/**
 * Registry of the suspension and host statuses for an application instance.
 *
 * @author oyving
 * @author Tony Vaagenes
 * @author bakksjo
 */
public interface MutableStatusRegistry extends AutoCloseable {

    /**
     * Returns the status of this application.
     */
    ApplicationInstanceStatus getStatus();

    /** Returns the host info of the given host. */
    HostInfo getHostInfo(HostName hostName);

    /**
     * Returns the set of all suspended hosts for this application.
     */
    Set<HostName> getSuspendedHosts();

    /**
     * Sets the state for the given host.
     */
    void setHostState(HostName hostName, HostStatus status);

    /**
     * Sets the orchestration status for the application instance.
     */
    void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus);

    /**
     * We don't want {@link AutoCloseable#close()} to throw an exception (what to do about it anyway?),
     * so we override it here to strip the exception from the signature.
     */
    @Override
    void close();

}

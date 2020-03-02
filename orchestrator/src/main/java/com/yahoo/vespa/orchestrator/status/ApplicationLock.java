// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

/**
 * The exclusive lock of an application in the status service, and methods to mutate host status
 * and data structures guarded by such a lock.
 *
 * @author oyving
 * @author Tony Vaagenes
 * @author bakksjo
 */
public interface ApplicationLock extends AutoCloseable {

    /** The reference of the locked application. */
    ApplicationInstanceReference getApplicationInstanceReference();

    /** Returns all host infos for this application. */
    HostInfos getHostInfos();

    /** Sets the state for the given host. */
    void setHostState(HostName hostName, HostStatus status);

    /** Returns the application status. */
    ApplicationInstanceStatus getApplicationInstanceStatus();

    /** Sets the orchestration status for the application instance. */
    void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus);

    /** WARNING: Must not throw an exception. */
    @Override
    void close();

}

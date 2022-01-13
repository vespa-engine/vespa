// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.service.monitor.ServiceHostListener;

import java.util.Set;
import java.util.function.Function;

/**
 * Service that can produce registries for the suspension of an application and its hosts.
 *
 * The registry class is per locked application instance.
 *
 * @author Øyvind Grønnesby
 * @author Tony Vaagenes
 * @author smorgrav
 */
public interface StatusService extends ServiceHostListener {

    /**
     * Returns a mutable host status registry for a locked application instance. All operations performed on
     * the returned registry are executed in the context of a lock, including read operations. Hence, multi-step
     * operations (e.g. read-then-write) are guaranteed to be consistent.
     */
    ApplicationLock lockApplication(OrchestratorContext context, ApplicationInstanceReference reference)
            throws UncheckedTimeoutException;

    /**
     * Returns all application instances that are allowed to be down. The intention is to use this
     * for visualization, informational and debugging purposes.
     *
     * @return A Map between the application instance and its status.
     */
    Set<ApplicationInstanceReference> getAllSuspendedApplications();

    /**
     * Returns a lambda, which when invoked for an application, returns an up-to-date snapshot of {@link HostInfos host infos}.
     *
     * <p>Unless the lock for the application is held, the returned snapshot may already be out of date.
     * (The snapshot itself is immutable.)</p>
     */
    Function<ApplicationInstanceReference, HostInfos> getHostInfosByApplicationResolver();

    /** Returns the status of the given application. This is consistent if its lock is held.*/
    ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationInstanceReference application);

    /** Get host info for hostname in application. This is consistent if its lock is held. */
    HostInfo getHostInfo(ApplicationInstanceReference reference, HostName hostName);
}

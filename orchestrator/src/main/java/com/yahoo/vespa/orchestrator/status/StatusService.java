// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;

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
public interface StatusService {

    /**
     * Returns a mutable host status registry for a locked application instance. All operations performed on
     * the returned registry are executed in the context of a lock, including read operations. Hence, multi-step
     * operations (e.g. read-then-write) are guaranteed to be consistent.
     *
     * Some limitations/caveats apply for certain implementations, and since clients of this API must be aware of
     * these limitations/caveats when using those implementations, they are expressed here, at interface level
     * rather than at implementation level, because the interface represents the lowest common denominator
     * of guarantees offered by implementations. Specifically, it is the zookeeper-based implementation's semantics
     * that "leak through" in this spec. Now, to the specific caveats:
     *
     * Locking this application instance only guarantees that the holder is the only one that can mutate host statuses
     * for the application instance.
     * It is _not_ safe to assume that there is only one entity holding the lock for a given application instance
     * reference at any given time.
     *
     * You cannot have multiple locks in a single thread, even if they are for different application instances,
     * (i.e. different HostStatusRegistry instances). (This is due to a limitation in SessionFailRetryLoop.)
     *
     * While read-then-write-operations are consistent (i.e. the current value doesn't change between the read
     * and the write), it is possible that the lock is lost before it is explicitly released by the code. In
     * this case, subsequent mutating operations will fail, but previous mutating operations are NOT rolled back.
     * This may leave the registry in an inconsistent state (as judged by the client code).
     */
    MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(
            OrchestratorContext context,
            ApplicationInstanceReference applicationInstanceReference) throws UncheckedTimeoutException;

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
    HostInfo getHostInfo(ApplicationInstanceReference applicationInstanceReference, HostName hostName);
}

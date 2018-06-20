// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;

import java.util.Set;

/**
 * Service that can produce registries for the suspension of an application
 * and its hosts.
 *
 * The registry classes are pr application instance.
 * TODO Remove readonly registry class (replace with actual methods) - only adds complexity.
 *
 * @author oyving
 * @author tonytv
 * @author smorgrav
 */
public interface StatusService {
    /**
     * Returns a readable host status registry for the given application instance. No locking is involved,
     * so this call will never block. However, since it is possible that mutations are going on simultaneously
     * with accessing this registry, the view obtained through the returned registry must be considered to be
     * possibly inconsistent snapshot values. It is not recommended that this method is used for anything other
     * than monitoring, logging, debugging, etc. It should never be used for multi-step operations (e.g.
     * read-then-write) where consistency is required. For those cases, use
     * {@link #lockApplicationInstance_forCurrentThreadOnly(ApplicationInstanceReference)}.
     */
    ReadOnlyStatusRegistry forApplicationInstance(ApplicationInstanceReference applicationInstanceReference);

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
    MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(ApplicationInstanceReference applicationInstanceReference);

    /** Lock application instance with timeout. */
    MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(
            ApplicationInstanceReference applicationInstanceReference,
            long timeoutSeconds);

    /**
     * Returns all application instances that are allowed to be down. The intention is to use this
     * for visualization, informational and debugging purposes.
     *
     * @return A Map between the application instance and its status.
     */
    Set<ApplicationInstanceReference> getAllSuspendedApplications();
}

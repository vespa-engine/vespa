// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Optional;
import java.util.Set;

/**
 * @author oyving
 */
public interface InstanceLookupService {

    Optional<ApplicationInstance> findInstanceById(ApplicationInstanceReference applicationInstanceReference);
    Optional<ApplicationInstance> findInstanceByHost(HostName hostName);
    Set<ApplicationInstanceReference> knownInstances();

    /**
     * Returns an application instance that may not contain more services and clusters than
     * those related to the host.
     */
    Optional<ApplicationInstance> findInstancePossiblyNarrowedToHost(HostName hostname);
}

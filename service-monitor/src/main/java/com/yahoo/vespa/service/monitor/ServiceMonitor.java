// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Optional;
import java.util.Set;

/**
 * The service monitor interface. A service monitor provides up to date information about the liveness status
 * (up, down or not known) of each service instance in a Vespa zone
 *
 * @author bratseth
 */
public interface ServiceMonitor {

    /**
     * Returns a ServiceModel which contains the current liveness status (up, down or unknown) of all instances
     * of all services of all clusters of all applications in a zone.
     *
     * <p>Please use the more specific methods below to avoid the cost of this method.</p>
     */
    ServiceModel getServiceModelSnapshot();

    default Set<ApplicationInstanceReference> getAllApplicationInstanceReferences() {
        return getServiceModelSnapshot().getAllApplicationInstances().keySet();
    }

    default Optional<ApplicationInstanceReference> getApplicationInstanceReference(HostName hostname) {
        return getApplication(hostname).map(ApplicationInstance::reference);
    }

    default Optional<ApplicationInstance> getApplication(HostName hostname) {
        return getServiceModelSnapshot().getApplication(hostname);
    }

    default Optional<ApplicationInstance> getApplication(ApplicationInstanceReference reference) {
        return getServiceModelSnapshot().getApplicationInstance(reference);
    }

    default Optional<ApplicationInstance> getApplicationNarrowedTo(HostName hostname) {
        return getApplication(hostname);
    }

    /**
     * Get notified of changes to the set of applications, or set of hosts assigned to an application.
     *
     * <p>When notified of model changes, the new model can be accessed through this interface
     * by the listener. The model changes are visible to other threads strictly after the listener
     * has been notified.</p>
     *
     * <p>WARNING: Methods on the listener may be invoked before returning from this method.</p>
     */
    default void registerListener(ServiceHostListener listener) { }
}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

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
     */
    ServiceModel getServiceModelSnapshot();

}

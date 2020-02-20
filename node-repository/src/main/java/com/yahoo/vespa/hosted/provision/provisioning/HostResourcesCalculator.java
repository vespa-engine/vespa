// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;

/**
 * @author freva
 */
public interface HostResourcesCalculator {

    /** Calculates the resources that are reserved for host level processes and returns the remainder. */
    NodeResources availableCapacityOf(String flavorName, NodeResources hostResources);

}

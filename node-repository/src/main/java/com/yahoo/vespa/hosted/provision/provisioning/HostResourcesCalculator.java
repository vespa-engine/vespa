// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.NodeResources;

/**
 * @author freva
 */
public interface HostResourcesCalculator {

    /**
     * Returns the advertised resources for this flavor, which may be more than the actual resources
     *
     * @param flavorName the name of the flavor
     * @param hostResources the real resources of the flavor
     * @return the advertised resources of this flavor, or the host resources if this flavor is not a host
     *         flavor with a difference between advertised and real resources
     */
    NodeResources availableCapacityOf(String flavorName, NodeResources hostResources);

}

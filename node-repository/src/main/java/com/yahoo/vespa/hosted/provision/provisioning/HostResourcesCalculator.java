// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;

/**
 * Some cloud providers advertise that a certain amount of resources are available in a flavor
 * but then actually provide somewhat less. This service provides the mapping between real and advertised
 * resources for all clouds.
 *
 * @author freva
 * @author bratseth
 */
public interface HostResourcesCalculator {

    /** Nodes use advertised resources. This returns the real resources for the node. */
    NodeResources realResourcesOf(Node node);

    /** Flavors use real resources. This returns the advertised resources of the flavor. */
    NodeResources advertisedResourcesOf(Flavor flavor);

}

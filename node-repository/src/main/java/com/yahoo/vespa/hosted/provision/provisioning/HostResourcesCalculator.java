// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;

/**
 * Some cloud providers advertise that a certain amount of resources are available in a flavor
 * but then actually provide somewhat less. This service provides the mapping between real and advertised
 * resources for all clouds.
 *
 * @author freva
 * @author bratseth
 */
public interface HostResourcesCalculator {

    /** Returns the real resources available on a node */
    NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository, boolean exclusive);

    /** Returns the advertised resources of a flavor */
    NodeResources advertisedResourcesOf(Flavor flavor);

    /**
     * Used with exclusive hosts:
     * Returns the lowest possible real resources we'll get if requesting the given advertised resources
     */
    NodeResources requestToReal(NodeResources advertisedResources, boolean exclusive);

    /**
     * Used with shared hosts:
     * Returns the advertised resources we need to request to be sure to get at least the given real resources.
     */
    NodeResources realToRequest(NodeResources realResources, boolean exclusive);

    /**
     * Returns the needed thin pool size in base2 Gb.
     */
    long thinPoolSizeInBase2Gb(NodeType nodeType, boolean sharedHost);

}

// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

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
    NodeResources realResourcesOf(Node node, NodeRepository nodeRepository);

    /** Returns the advertised resources of a flavor */
    NodeResources advertisedResourcesOf(Flavor flavor);

    /**
     * Returns the highest possible overhead (difference between advertised and real) which may result
     * from requesting the given advertised resources
     *
     * @return a NodeResources containing the *difference* between the given advertised resources
     *         and the (worst case) real resources we'll observe. This is always compatible with the
     *         given resources.
     */
    NodeResources overheadAllocating(NodeResources resources, boolean exclusive);

}

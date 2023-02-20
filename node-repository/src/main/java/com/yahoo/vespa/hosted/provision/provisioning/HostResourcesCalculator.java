// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;

/**
 * Some cloud providers advertise that a certain amount of resources are available in a flavor
 * but then actually provide less.
 *
 * This class converts between real and advertised resources for all clouds.
 *
 * @author freva
 * @author bratseth
 */
public interface HostResourcesCalculator {

    /** Returns the real resources available on a node */
    NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository);

    /** Returns the advertised resources of a flavor */
    NodeResources advertisedResourcesOf(Flavor flavor);

    /**
     * Used with exclusive hosts:
     * Returns the lowest possible real resources we'll get if requesting the given advertised resources
     */
    NodeResources requestToReal(NodeResources advertisedResources, boolean exclusiveAllocation, boolean bestCase);

    /**
     * Used with shared hosts:
     * Returns the advertised resources we need to request to be sure to get at least the given real resources.
     */
    NodeResources realToRequest(NodeResources realResources, boolean exclusiveAllocation, boolean bestCase);

    /**
     * Returns the disk space to reserve in base2 GB. This space is reserved for use by the host, e.g. for storing
     * container images.
     */
    long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost);

}

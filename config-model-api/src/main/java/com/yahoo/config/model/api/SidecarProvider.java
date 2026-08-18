// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SidecarSpec;

import java.util.List;

/**
 * Provides sidecar containers to run alongside cluster nodes. The config model decides when sidecars
 * are used, while the implementation of this interface decides what the sidecar containers look like
 * (image, resources, command, probes). The implementation is injected into the config server as a
 * component, so that deployment forms (e.g. hosted Vespa) can provide sidecar configuration without
 * this repository knowing it. At most one provider may be registered.
 *
 * @author glebashnik
 */
public interface SidecarProvider {

    /**
     * Returns sidecars to run alongside the nodes of the given cluster. Invoked before node
     * provisioning. Sidecar ids and names must be unique and stable within a cluster.
     *
     * @param clusterId        the id of the container cluster the sidecars will run alongside
     * @param minNodeResources the minimum resources of the nodes in the cluster
     * @param needTriton       whether the cluster needs a Triton sidecar
     */
    List<SidecarSpec> getSidecars(ClusterSpec.Id clusterId, NodeResources minNodeResources, boolean needTriton);

}

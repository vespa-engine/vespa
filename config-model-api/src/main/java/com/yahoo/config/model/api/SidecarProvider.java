// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SidecarSpec;

import java.util.List;
import java.util.Set;

/**
 * Provides sidecar containers to run alongside cluster nodes. The config model decides what sidecars
 * are needed, while the implementation of this interface decides how the sidecar containers look like
 * (image, resources, command, probes). The implementation can be injected into the config server as a
 * component. At most one provider may be registered.
 *
 * @author glebashnik
 */
public interface SidecarProvider {

    /** The name for the Triton sidecar. */
    String TRITON_SIDECAR_NAME = "triton";

    /**
     * Returns sidecars to run alongside the nodes of the given cluster. Invoked before node
     * provisioning. Sidecar ids and names must be unique and stable within a cluster.
     * This legacy method is preserved for backwards compatibility with old config models;
     */
    List<SidecarSpec> getSidecars(ClusterSpec.Id clusterId, NodeResources minNodeResources, boolean needTriton);

    /**
     /**
     * Returns sidecars to run alongside the nodes of the given cluster. Invoked before node
     * provisioning. Sidecar ids and names must be unique and stable within a cluster.
     * Application and vespaVersion are added to support corresponding dimensions in feature flags.
     */
    default List<SidecarSpec> getSidecars(ApplicationId application, Version vespaVersion, ClusterSpec.Id clusterId,
                                          NodeResources minNodeResources, Set<String> neededSidecars) {
        return getSidecars(clusterId, minNodeResources, neededSidecars.contains(TRITON_SIDECAR_NAME));
    }

}

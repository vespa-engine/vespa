// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.api.ConfigModelPlugin;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;

import java.util.List;

/**
 * Provides sidecar containers to run alongside cluster nodes. 
 * The implementation is injected into the config server as a component and picked up through 
 * the config model plugin registry.
 * At most one provider may be registered.
 *
 * @author glebashnik
 */
public interface SidecarProvider extends ConfigModelPlugin {

    /**
     * Returns sidecars to run alongside the nodes of the given cluster. Invoked before node
     * provisioning. Sidecar ids and names must be unique and stable within a cluster.
     *
     * @param needTriton whether the cluster needs a Triton sidecar
     */
    List<SidecarSpec> getSidecars(ApplicationContainerCluster cluster, NodesSpecification nodesSpecification, DeployState deployState, boolean needTriton);

}

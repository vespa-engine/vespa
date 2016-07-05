// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NodeAdmin manages the life cycle of NodeAgents.
 * @author dybis
 */
public interface NodeAdmin {

    /**
     * Calling this will cause NodeAdmin to move to the state containersToRun by adding or removing nodes.
     * @param containersToRun this is the wanted state.
     */
    void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun);

    /**
     * Causes the NodeAgents to freeze, meaning they will not pick up any changes from NodeRepository.
     * @return if NodeAgent is frozen.
     */
    boolean freezeAndCheckIfAllFrozen();

    /**
     * Causes the NodeAgent to unfreeze and start picking up changes from NodeRepository.
     */
    void unfreeze();

    /**
     * Returns list of hosts.
     */
    Set<HostName> getListOfHosts();

    /**
     * Returns list of hosts with active nodes.
     */
    Set<HostName> getHostNamesOfActiveNodes();

    /**
     * Returns a map containing all relevant NodeAdmin variables and their current values.
     * Do not try to parse output or use in tests.
     */
    Map<String, Object> debugInfo();

    /**
     * Stop the NodeAgent. Will not delete the storage or stop the container.
     */
    void shutdown();
}

// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;

import java.util.List;
import java.util.Set;

/**
 * API for NodeAdmin seen from outside.
 * @author dybis
 */
public interface NodeAdmin {

    void refreshContainersToRun(final List<ContainerNodeSpec> containersToRun);

    boolean freezeAndCheckIfAllFrozen();

    void unfreeze();

    Set<HostName> getListOfHosts();

    String debugInfo();

    void shutdown();
}

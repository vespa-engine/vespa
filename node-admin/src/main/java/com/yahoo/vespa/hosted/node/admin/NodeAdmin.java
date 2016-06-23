package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.vespa.applicationmodel.HostName;

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
}

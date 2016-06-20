package com.yahoo.vespa.hosted.node.admin;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.List;
import java.util.Set;

/**
 * API for NodeAdmin seen from outside.
 * @author dybis
 */
public interface NodeAdmin {

     void setState(final List<ContainerNodeSpec> containersToRun);

     boolean setFreezeAndCheckIfAllFrozen(boolean freeze);

     Set<HostName> getListOfHosts();
}

package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.NodeAdmin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock with some simple logic
 *
 * @author dybis
 */
public class NodeAdminMock implements NodeAdmin {

    StringBuilder info = new StringBuilder();

    Set<HostName> hostnames = new HashSet<>();

    boolean freezeSetState = false;
    public AtomicBoolean frozen = new AtomicBoolean(false);

    // We make it threadsafe as the test have its own peeking thread.
    private Object monitor = new Object();

    @Override
    public void refreshContainersToRun(List<ContainerNodeSpec> containersToRun) {
        synchronized (monitor) {
            hostnames.clear();
            containersToRun.forEach(container -> hostnames.add(container.hostname));
        }
    }

    @Override
    public boolean freezeAndCheckIfAllFrozen() {
        info.append(" Freeze called while in state " + frozen.get());
        freezeSetState = true;
        return frozen.get();
    }

    @Override
    public void unfreeze() {
        info.append(" Unfreeze called while in state " + frozen.get());
        freezeSetState = false;
    }

    @Override
    public Set<HostName> getListOfHosts() {
        synchronized (monitor) {
            return hostnames;
        }
    }

    /*
     * We use this to get some information easily out of the mock in the integration test here.
     */
    @Override
    public String debugInfo() {
        return info.toString();
    }
}

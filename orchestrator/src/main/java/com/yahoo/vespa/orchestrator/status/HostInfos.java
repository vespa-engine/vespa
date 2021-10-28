// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Map;
import java.util.Set;

/**
 * Collection of the suspended hosts of an application.
 *
 * @author hakonhall
 */
// @Immutable
public class HostInfos {
    private final Map<HostName, HostInfo> hostInfos;

    public HostInfos(Map<HostName, HostInfo> hostInfos) {
        this.hostInfos = Map.copyOf(hostInfos);
    }

    public HostInfos() {
        this.hostInfos = Map.of();
    }

    /** Get host info for hostname, returning a NO_REMARKS HostInfo if unknown. */
    public HostInfo getOrNoRemarks(HostName hostname) {
        return hostInfos.getOrDefault(hostname, HostInfo.createNoRemarks());
    }

    /** The set of hostnames that were set in ZooKeeper - used for removing orphaned hostnames. */
    public Set<HostName> getZkHostnames() {
        return Set.copyOf(hostInfos.keySet());
    }
}

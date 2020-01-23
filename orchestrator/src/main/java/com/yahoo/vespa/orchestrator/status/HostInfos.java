// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collection of suspended hosts.
 *
 * @author hakonhall
 */
public class HostInfos {
    private final Map<HostName, HostInfo> hostInfos;

    public HostInfos(Map<HostName, HostInfo> hostInfos) {
        this.hostInfos = Map.copyOf(hostInfos);
    }

    /** Get all suspended hostnames. */
    public Set<HostName> suspendedHostsnames() {
        return hostInfos.entrySet().stream()
                .filter(entry -> entry.getValue().status() != HostStatus.NO_REMARKS)
                .map(entry -> entry.getKey())
                .collect(Collectors.toSet());
    }

    /** Get host info for hostname, returning a NO_REMARKS HostInfo if unknown. */
    public HostInfo get(HostName hostname) {
        return hostInfos.getOrDefault(hostname, HostInfo.createNoRemarks());
    }
}

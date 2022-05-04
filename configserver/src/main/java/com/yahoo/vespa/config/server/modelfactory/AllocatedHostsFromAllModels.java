// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Different model versions being built may disagree on the set of hosts that should be allocated.
 * This keeps track of the right set of hosts to finally allocate given such disagreement:
 * The superset of hosts allocated on all model versions, with the spec of the newest version allocating it.
 *
 * @author bratseth
 */
public class AllocatedHostsFromAllModels {

    /** All hosts with the newest model version that allocated it, indexed on hostname */
    private final Map<String, Pair<HostSpec, Version>> hosts = new LinkedHashMap<>();

    /** Adds the nodes allocated for a particular model version. */
    public void add(AllocatedHosts allocatedHosts, Version version) {
        for (var newHost : allocatedHosts.getHosts()) {
            var presentHost = hosts.get(newHost.hostname());
            if (presentHost == null || version.isAfter(presentHost.getSecond()))
                hosts.put(newHost.hostname(), new Pair<>(newHost, version));
        }
    }

    public AllocatedHosts toAllocatedHosts() {
        // Preserve add order for tests
        Set<HostSpec> hostSet = new LinkedHashSet<>();
        for (var host : hosts.values())
            hostSet.add(host.getFirst());
        return AllocatedHosts.withHosts(hostSet);
    }

}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The hosts allocated to an application.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class AllocatedHosts {

    private final Set<HostSpec> hosts;

    private AllocatedHosts(Set<HostSpec> hosts) {
        this.hosts = new LinkedHashSet<>(hosts); // Preserve order for tests
    }

    public static AllocatedHosts withHosts(Set<HostSpec> hosts) {
        return new AllocatedHosts(hosts);
    }

    /** Returns the hosts of this allocation */
    public Set<HostSpec> getHosts() { return hosts; }

    /** Returns the hosts of this allocation grouped by their cluster */
    public Map<ClusterSpec.Id, List<HostSpec>> getHostsByCluster() {
        return hosts.stream().collect(Collectors.groupingBy(host -> host.membership().get().id()));
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof AllocatedHosts)) return false;
        return ((AllocatedHosts) other).hosts.equals(this.hosts);
    }
    
    @Override
    public int hashCode() {
        return hosts.hashCode();
    }

    @Override
    public String toString() {
        return hosts.toString();
    }

}

package com.yahoo.config.provision;

import java.util.Collection;

/**
 * A spec of the hosts in a cluster.
 *
 * @author bratseth
 */
public class ClusterHosts {

    private final ClusterSpec cluster;
    private final Collection<HostSpec> hosts;

    public ClusterHosts(ClusterSpec cluster, Collection<HostSpec> hosts) {
        this.cluster = cluster;
        this.hosts = hosts;
    }

    public ClusterSpec cluster() { return cluster; }

    public Collection<HostSpec> hosts() { return hosts; }

}

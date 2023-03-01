// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.Objects;

/**
 * A physical host, running a set of services.
 * The identity of a host is its hostname. Hosts are comparable on their host name.
 *
 * @author gjoranv
 */
public final class Host extends TreeConfigProducer<AnyConfigProducer> implements SentinelConfig.Producer, Comparable<Host> {

    private ConfigSentinel configSentinel = null;
    private final String hostname;
    private final boolean runsConfigServer;

    /**
     * Constructs a new Host instance.
     *
     * @param parent   parent TreeConfigProducer in the config model.
     * @param hostname hostname for this host.
     */
    public Host(TreeConfigProducer<? super Host> parent, String hostname) {
        this(parent, hostname, false);
    }

    private Host(TreeConfigProducer<? super Host> parent, String hostname, boolean runsConfigServer) {
        super(parent, hostname);
        Objects.requireNonNull(hostname, "The host name of a host cannot be null");
        this.runsConfigServer = runsConfigServer;
        this.hostname = hostname;
    }

    public static Host createConfigServerHost(HostSystem hostSystem, String hostname) {
        return new Host(hostSystem, hostname, true);
    }
    public static Host createHost(HostSystem hostSystem, String hostname) {
        return new Host(hostSystem, hostname, false);
    }

    public String getHostname() {
        return hostname;
    }

    public boolean runsConfigServer() {
        return runsConfigServer;
    }

    /** Returns the string representation of this Host object. */
    public String toString() {
        return "host '" + getHostname() + "'";
    }

    @Override
    public void getConfig(SentinelConfig.Builder builder) {
        // TODO (MAJOR_RELEASE): This shouldn't really be here, but we need to make sure users can upgrade if we change sentinel to use hosts/<hostname>/sentinel instead of hosts/<hostname>
        // as config id. We should probably wait for a major release
        if (configSentinel != null) {
            configSentinel.getConfig(builder);
        }
    }

    public void setConfigSentinel(ConfigSentinel configSentinel) {
        this.configSentinel = configSentinel;
    }

    @Override
    public int hashCode() { return hostname.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Host)) return false;
        return ((Host)other).hostname.equals(hostname);
    }

    @Override
    public int compareTo(Host other) {
        return this.hostname.compareTo(other.hostname);
    }

}

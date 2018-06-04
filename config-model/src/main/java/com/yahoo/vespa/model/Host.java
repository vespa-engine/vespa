// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import com.yahoo.cloud.config.SentinelConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * A physical host, running a set of services.
 * The identity of a host is its hostname. Hosts are comparable on their host name.
 *
 * @author gjoranv
 */
public final class Host extends AbstractConfigProducer<AbstractConfigProducer<?>> implements SentinelConfig.Producer, Comparable<Host> {

    private ConfigSentinel configSentinel = null;
    private final String hostname;
    private final boolean runsConfigServer;

    /**
     * Constructs a new Host instance.
     *
     * @param parent   parent AbstractConfigProducer in the config model.
     * @param hostname hostname for this host.
     */
    public Host(AbstractConfigProducer parent, String hostname) {
        this(parent, hostname, false);
    }

    private Host(AbstractConfigProducer parent, String hostname, boolean runsConfigServer) {
        super(parent, hostname);
        Objects.requireNonNull(hostname, "The host name of a host cannot be null");
        this.runsConfigServer = runsConfigServer;
        this.hostname = hostname;
        if (parent instanceof HostSystem)
            checkName((HostSystem) parent, hostname);            
    }

    private void checkName(HostSystem parent, String hostname) {
        // Give a warning if the host does not exist
        try {
            Object address = java.net.InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            deployLogger().log(Level.WARNING, "Unable to lookup IP address of host: " + hostname);
        }
        if (! hostname.contains(".")) {
            deployLogger().log(Level.WARNING, "Host named '" + hostname + "' may not receive any config " +
                                              "since it is not a canonical hostname");
        }
    }

    public static Host createConfigServerHost(AbstractConfigProducer parent, String hostname) {
        return new Host(parent, hostname, true);
    }

    // For testing
    Host(AbstractConfigProducer parent) {
        super(parent, "testhost");
        hostname = "testhost";
        configSentinel = null;
        runsConfigServer = false;
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
    public void writeFiles(File directory) throws IOException {
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

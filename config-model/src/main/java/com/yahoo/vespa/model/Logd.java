// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.cloud.config.log.LogdConfig;

/**
 * There is one logd running on each Vespa host, and one instance of
 * this class is therefore created by each instance of class {@link
 * Host}.
 *
 * @author gjoranv
 */
public class Logd
    extends AbstractService
    implements LogdConfig.Producer
{

    /**
     * Creates a new Logd instance.
     */
    public Logd(Host host) {
        super(host, "logd");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
        portsMeta.on(0).tag("http").tag("state");
    }

    /**
     * Logd needs a state port.
     *
     * @return The number of ports reserved by the logd
     */
    public int getPortCount() { return 1; }

    @Override
    public String[] getPortSuffixes() {
        String[] suffixes = new String[1];
        suffixes[0] = "http";
        return suffixes;
    }

    /** Returns the desired base port for this service.  */
    public int getWantedPort() { return 19089; }

    /**
     * @return The command used to start logd
     */
    public String getStartupCommand() { return "exec sbin/vespa-logd"; }

    @Override
    public int getHealthPort() { return getRelativePort(0); }

    public void getConfig(LogdConfig.Builder builder) {
        builder.stateport(getHealthPort());
    }
}

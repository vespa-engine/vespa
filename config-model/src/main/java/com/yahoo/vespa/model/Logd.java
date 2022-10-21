// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.cloud.config.log.LogdConfig;
import java.util.Optional;

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
    static final int BASEPORT = 19089;

    /**
     * Creates a new Logd instance.
     */
    public Logd(Host host) {
        super(host, "logd");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
        portsMeta.on(0).tag("http").tag("state");
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        from.wantPort(start, "http");
    }

    /**
     * Logd needs a state port.
     *
     * @return The number of ports reserved by the logd
     */
    public int getPortCount() { return 1; }

    /** Returns the desired base port for this service.  */
    public int getWantedPort() { return 19089; }

    /**
     * @return The command used to start logd
     */
    @Override
    public Optional<String> getStartupCommand() { return Optional.of("exec sbin/vespa-logd"); }

    @Override
    public int getHealthPort() { return getRelativePort(0); }

    public void getConfig(LogdConfig.Builder builder) {
        builder.stateport(getHealthPort());
    }
}

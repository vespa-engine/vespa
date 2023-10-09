// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

/**
 * There is one config proxy running on each Vespa host, and one instance of
 * this class is therefore created by each instance of class {@link
 * com.yahoo.vespa.model.Host}.
 *
 * NOTE: The Config proxy is not started by the config system, and
 * does not receive any config. It's included here so we know what host
 * it runs on, and to give an error message if another service tries
 * to reserve the port it is using.
 *
 * @author Vidar Larsen
 * @author Harald Musum
 */
public class ConfigProxy extends AbstractService {

    public final static int BASEPORT = 19090;

    /**
     * Creates a new ConfigProxy instance.
     *
     * @param host hostname
     */
    public ConfigProxy(Host host) {
        super(host, "configproxy");
        portsMeta.on(0).tag("rpc").tag("client").tag("status").tag("rpc").tag("admin");
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        from.requirePort(start, "rpc");
    }

    /**
     * Returns the desired base port for this service.
     */
    public int getWantedPort() { return BASEPORT; }

    /**
     * The desired base port is the only allowed base port.
     */
    public boolean requiresWantedPort() { return true; }

    /**
     * ConfigProxy needs one rpc client port.
     *
     * @return The number of ports reserved by the config proxy
     */
    public int getPortCount() { return 1; }

}

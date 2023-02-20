// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;


/**
 * Represents a Configserver. There may be one or more Configservers in a
 * Vespa system.
 *
 * NOTE: The Configserver is not started by the config system, and
 * does not receive any config. It's included here so we know what host
 * it runs on, and to give an error message if another service tries
 * to reserve the ports it is using.
 *
 * @author  gjoranv
 */
public class Configserver extends AbstractService {
    private static final long serialVersionUID = 1L;
    public static final int defaultRpcPort = 19070;

    private final int rpcPort;

    public Configserver(TreeConfigProducer<? super Configserver> parent, String name, int rpcPort) {
        super(parent, name);
        this.rpcPort = rpcPort;
        portsMeta.on(0).tag("rpc").tag("config");
        portsMeta.on(1).tag("http").tag("config").tag("state");
        setProp("clustertype", "admin");
        setProp("clustername", "admin");
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (requiresWantedPort()) {
            from.requirePort(start++, "rpc");
            from.requirePort(start++, "http");
        } else if (start == 0) {
            from.allocatePort("rpc");
            from.allocatePort("http");
        } else {
            from.wantPort(start++, "rpc");
            from.wantPort(start++, "http");
        }
    }


    /**
     * Returns the desired base port for this service.
     */
    public int getWantedPort() {
        return rpcPort;
    }

    /**
     * The desired base port is the only allowed base port.
     * @return 'true' always
     */
    public boolean requiresWantedPort() {
        return getId() < 2;
    }

     /**
     * @return the number of ports needed by the configserver.
     */
    public int getPortCount() { return 2; }

    private int getConfigServerRpcPort() {
        return getRelativePort(0);
    }

    private int getConfigServerHttpPort() {
        return getRelativePort(1);
    }

    @Override
    public int getHealthPort()  {
        return getRelativePort(1);
    }


}

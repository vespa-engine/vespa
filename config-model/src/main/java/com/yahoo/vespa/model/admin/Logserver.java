// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;

/**
 * Represents the Logserver. There is exactly one logserver in a Vespa
 * system.
 *
 * @author gjoranv
 * @author bjorncs
 */
public class Logserver extends AbstractService {

    private static final long serialVersionUID = 1L;
    private static final String logArchiveDir = "$ROOT/logs/vespa/logarchive";

    public Logserver(AbstractConfigProducer parent) {
        super(parent, "logserver");
        portsMeta.on(0).tag("logtp").tag("rpc");
        portsMeta.on(1).tag("unused");
        portsMeta.on(2).tag("unused");
        portsMeta.on(3).tag("unused");
        setProp("clustertype", "admin");
        setProp("clustername", "admin");
    }

    /**
     * @return the startup command for the logserver
     */
    public String getStartupCommand() {
        return "exec $ROOT/bin/vespa-logserver-start " + getMyJVMArgs() + " " + getJvmOptions();
    }

    /**
     * @return the jvm args to be used by the logserver.
     */
    private String getMyJVMArgs() {
        StringBuilder sb = new StringBuilder();
        sb.append("-Dlogserver.rpcListenPort=").append(getRelativePort(0));
        sb.append(" ");
        sb.append("-Dlogserver.logarchive.dir=" + logArchiveDir);
        return sb.toString();
    }

    /**
     * Returns the desired base port for this service.
     */
    public int getWantedPort() {
        return 19080;
    }

    /**
     * The desired base port is the only allowed base port.
     *
     * @return 'true' always
     */
    public boolean requiresWantedPort() {
        return true; // TODO Support dynamic port allocation for logserver
    }

    /**
     * @return the number of ports needed by the logserver.
     */
    public int getPortCount() {
        return 4;
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        int port = (start == 0) ? getWantedPort() : start;
        from.requirePort(port++, "rpc");
        from.requirePort(port++, "unused/1");
        from.requirePort(port++, "unused/2");
        from.requirePort(port++, "unused/3");
    }

}

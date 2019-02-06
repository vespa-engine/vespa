// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

/**
 * Represents the Logserver. There is exactly one logserver in a Vespa
 * system.
 *
 * @author gjoranv
 */
public class Logserver extends AbstractService {

    private static final long serialVersionUID = 1L;
    private static final String logArchiveDir = "$ROOT/logs/vespa/logarchive";

    public Logserver(AbstractConfigProducer parent) {
        super(parent, "logserver");
        portsMeta.on(0).tag("unused");
        portsMeta.on(1).tag("logtp");
        portsMeta.on(2).tag("logtp").tag("telnet").tag("last-errors-holder");
        portsMeta.on(3).tag("logtp").tag("telnet").tag("replicator");
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
        sb.append("-Dlogserver.listenport=").append(getRelativePort(1));
        sb.append(" ");
        sb.append("-Dlogserver.last-errors-holder.port=").append(getRelativePort(2));
        sb.append(" ");
        sb.append("-Dlogserver.replicator.port=").append(getRelativePort(3));
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
        return true;
    }

    /**
     * @return the number of ports needed by the logserver.
     */
    public int getPortCount() {
        return 4;
    }

    @Override
    public String[] getPortSuffixes() {
        return new String[]{ "unused", "logtp", "last.errors", "replicator" };
    }

}

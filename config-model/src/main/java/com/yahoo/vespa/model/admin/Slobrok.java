// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.config.core.StateserverConfig;
import com.yahoo.vespa.model.AbstractService;

/**
 * Represents a Slobrok service.
 *
 * @author  gjoranv
 */
public class Slobrok extends AbstractService implements StateserverConfig.Producer {
    private static final long serialVersionUID = 1L;

    @Override
    public void getConfig(StateserverConfig.Builder builder) {
        builder.httpport(getStatePort());
    }

    /**
     * @param parent The parent ConfigProducer.
     * @param index  unique index for all slobroks
     */
    public Slobrok(AbstractConfigProducer parent, int index) {
        super(parent, "slobrok." + index);
        portsMeta.on(0).tag("rpc").tag("admin").tag("status");
        portsMeta.on(1).tag("http").tag("state");
        setProp("index", index);
        setProp("clustertype", "slobrok");
        setProp("clustername", "admin");
    }

    @Override
    public boolean requiresConsecutivePorts() {
        return false;
    }

    @Override
    public int getWantedPort() {
        if (getId() == 1) {
            return 19099;
        } else {
            return 0;
        }
    }

    public String getStartupCommand() {
        return "exec $ROOT/sbin/vespa-slobrok -p " + getPort() + " -c " + getConfigId();
    }

    /**
     * @return The number of ports needed by the slobrok.
     */
    public int getPortCount() {
        return 2;
    }

    /**
     * @return The port on which this slobrok should respond, as a String.
     */
    private String getPort() {
        return String.valueOf(getRelativePort(0));
    }

    /**
     * @return The port on which the state server should respond
     */
    public int getStatePort() {
        return getRelativePort(1);
    }

    /**
     * @return The connection spec to this Slobrok
     */
    public String getConnectionSpec() {
        return "tcp/" + getHostName() + ":" + getPort();
    }

}

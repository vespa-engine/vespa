// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.config.core.StateserverConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import java.util.Optional;

/**
 * Represents a Slobrok service.
 *
 * @author gjoranv
 */
public class Slobrok extends AbstractService implements StateserverConfig.Producer {

    private static final long serialVersionUID = 1L;

    public final static int BASEPORT = 19099;

    @Override
    public void getConfig(StateserverConfig.Builder builder) {
        builder.httpport(getHealthPort());
    }

    /**
     * @param parent the parent ConfigProducer.
     * @param index  unique index for all slobroks
     */
    public Slobrok(AbstractConfigProducer<?> parent, int index,
                   ModelContext.FeatureFlags featureFlags)
    {
        super(parent, "slobrok." + index);
        portsMeta.on(0).tag("rpc").tag("admin").tag("status");
        portsMeta.on(1).tag("http").tag("state");
        setProp("index", index);
        setProp("clustertype", "slobrok");
        setProp("clustername", "admin");
    }

    @Override
    public int getWantedPort() {
        if (getId() == 1) {
            return BASEPORT;
        } else {
            return 0;
        }
    }

    public Optional<String> getStartupCommand() {
        return Optional.of("exec $ROOT/sbin/vespa-slobrok -N -p " + getRpcPort() + " -c " + getConfigId());
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        from.wantPort(start, "rpc");
        from.allocatePort("http");
    }

    /**
     * @return The number of ports needed by the slobrok.
     */
    public int getPortCount() {
        return 2;
    }

    /**
     * @return The port on which this slobrok should respond
     */
    private int getRpcPort() {
        return getRelativePort(0);
    }

    /**
     * @return The port on which the state server should respond
     */
    @Override
    public int getHealthPort() {
        return getRelativePort(1);
    }

    /**
     * @return The connection spec to this Slobrok
     */
    public String getConnectionSpec() {
        return "tcp/" + getHostName() + ":" + getRpcPort();
    }

}

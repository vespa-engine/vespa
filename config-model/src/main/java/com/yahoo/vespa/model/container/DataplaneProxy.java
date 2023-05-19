// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;

import java.util.Optional;

public class DataplaneProxy extends AbstractService implements DataplaneProxyConfig.Producer {

    private final Integer port;

    public DataplaneProxy(TreeConfigProducer<? super DataplaneProxy> parent, Integer port) {
        super(parent, "dataplane-proxy");
        this.port = port;
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }


    // Does not need any ports.
    @Override
    public void allocatePorts(int start, PortAllocBridge from) { }

    /**
     *
     * @return The number of ports reserved
     */
    public int getPortCount() { return 0; }

    /**
     * @return The command used to start
     */
    @Override
    public Optional<String> getStartupCommand() { return Optional.of("exec $ROOT/bin/vespa-dataplane-proxy-start -c " + getConfigId()); }

    @Override
    public void getConfig(DataplaneProxyConfig.Builder builder) {
        builder.port(port);
    }

    @Override
    public Optional<String> getPreShutdownCommand() {
        var builder = new DataplaneProxyConfig.Builder();
        getConfig(builder);
        String cmd = "$ROOT/bin/vespa-dataplane-proxy-start -S -c " + getConfigId();
        return Optional.of(cmd);
    }

}

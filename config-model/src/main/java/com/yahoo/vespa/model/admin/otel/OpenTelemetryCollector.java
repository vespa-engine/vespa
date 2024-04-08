// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.otel;

import com.yahoo.cloud.config.OpenTelemetryConfig;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;

import java.util.Optional;

public class OpenTelemetryCollector extends AbstractService implements OpenTelemetryConfig.Producer  {

    private final String config;

    public OpenTelemetryCollector(TreeConfigProducer<?> parent, String config) {
        super(parent, "otelcol");
        setProp("clustertype", "admin");
        setProp("clustername", "admin");
        this.config = config;
    }

    /**
     * @return the startup command for the otelcol wrapper
     */
    @Override
    public Optional<String> getStartupCommand() {
        return Optional.of("exec $ROOT/bin/vespa-otelcol-start");
    }


    @Override
    public void allocatePorts(int start, PortAllocBridge from) {}

    @Override
    public int getPortCount() {
        return 0;
    }

    @Override
    public void getConfig(OpenTelemetryConfig.Builder builder) {
        builder.config(config);
    }
}

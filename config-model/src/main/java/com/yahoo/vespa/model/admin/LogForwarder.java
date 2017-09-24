// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.LogforwarderConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

import java.util.ArrayList;
import java.util.List;

public class LogForwarder extends AbstractService implements LogforwarderConfig.Producer {

    private final String type;
    private final List<String> sources;
    private final String endpoints;
    private final String indexName;

    /**
     * Creates a new LogForwarder instance.
     */
    // TODO: Use proper types?
    public LogForwarder(AbstractConfigProducer parent, int index, String type, List<String> sources, String endpoints, String indexName) {
        super(parent, "logforwarder." + index);
        this.type = type;
        this.sources = sources;
        this.endpoints = endpoints;
        this.indexName = indexName;
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    /**
     * LogForwarder does not need any ports.
     *
     * @return The number of ports reserved by the LogForwarder
     */
    public int getPortCount() { return 0; }

    /**
     * @return The command used to start LogForwarder
     */
    public String getStartupCommand() { return "exec $ROOT/libexec/vespa/vespa-logforwarder-start " + getConfigId(); }

    @Override
    public void getConfig(LogforwarderConfig.Builder builder) {
        List<LogforwarderConfig.Sources.Builder> sourceList = new ArrayList<>();
        for (String s : this.sources) {
            LogforwarderConfig.Sources.Builder source = new LogforwarderConfig.Sources.Builder();
            source.log(s);
            sourceList.add(source);
        }

        builder.type(type);
        builder.endpoint(endpoints);
        builder.index(indexName);
        builder.sources(sourceList);
    }

}

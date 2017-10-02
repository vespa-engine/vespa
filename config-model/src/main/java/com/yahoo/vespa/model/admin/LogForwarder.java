// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.LogforwarderConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.AbstractService;

import java.util.ArrayList;
import java.util.List;

public class LogForwarder extends AbstractService implements LogforwarderConfig.Producer {

    public static class Config {
        public final String deploymentServer;
        public final String clientName;

        private Config(String ds, String cn) {
            this.deploymentServer = ds;
            this.clientName = cn;
        }
        public Config withDeploymentServer(String ds) {
            return new Config(ds, clientName);
        }
        public Config withClientName(String cn) {
            return new Config(deploymentServer, cn);
        }
    }

    private final Config config;

    /**
     * Creates a new LogForwarder instance.
     */
    // TODO: Use proper types?
    public LogForwarder(AbstractConfigProducer parent, int index, Config config) {
        super(parent, "logforwarder." + index);
        this.config = config;
        setProp("clustertype", "hosts");
        setProp("clustername", "admin");
    }

    public static Config cfg() {
        return new Config(null, null);
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
    public String getStartupCommand() { return "exec $ROOT/libexec/vespa/vespa-logforwarder-start -c " + getConfigId(); }

    @Override
    public void getConfig(LogforwarderConfig.Builder builder) {
        builder.deploymentServer(config.deploymentServer);
        builder.clientName(config.clientName);
    }

}

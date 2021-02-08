// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.ConnectionLogComponent;
import com.yahoo.vespa.model.container.configserver.ConfigserverCluster;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import org.w3c.dom.Element;

/**
 * Builds the config model for the standalone config server.
 *
 * @author Ulf Lilleengen
 */
public class ConfigServerContainerModelBuilder extends ContainerModelBuilder {

    private final CloudConfigOptions options;

    public ConfigServerContainerModelBuilder(CloudConfigOptions options) {
        super(true, Networking.enable);
        this.options = options;
    }

    @Override
    public void doBuild(ContainerModel model, Element spec, ConfigModelContext modelContext) {
        ConfigserverCluster cluster = new ConfigserverCluster(modelContext.getParentProducer(), "configserver",
                                                              options);
        super.doBuild(model, spec, modelContext.withParent(cluster));
        cluster.setContainerCluster(model.getCluster());
    }

    // Need to override this method since we need to use the values in CloudConfigOptions (the ones
    // in ConfigModelContext.DeployState.properties are not set)
    @Override
    protected void addStatusHandlers(ApplicationContainerCluster cluster, boolean isHostedVespa) {
        super.addStatusHandlers(cluster, isHosted());
    }

    // Override access log configuration for hosted configserver/controller
    @Override
    protected void addAccessLogs(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        if (isHosted()){
            cluster.addComponent(
                    new AccessLogComponent(
                            cluster, AccessLogComponent.AccessLogType.jsonAccessLog, AccessLogComponent.CompressionType.ZSTD,
                            "logs/vespa/configserver/access-json.log.%Y%m%d%H%M%S", null, true, true, "access-json.log"));
            cluster.addComponent(new ConnectionLogComponent(cluster, FileConnectionLog.class, "configserver"));
        } else {
            super.addAccessLogs(deployState, cluster, spec);
        }
    }

    @Override
    protected void addHttp(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        super.addHttp(deployState, spec, cluster, context);
        cluster.getHttp().getHttpServer().get().setHostedVespa(isHosted());
    }

    /** Note: using {@link CloudConfigOptions} as {@link DeployState#isHosted()} returns <em>false</em> for hosted configserver/controller */
    private boolean isHosted() { return options.hostedVespa().orElse(Boolean.FALSE); }
}

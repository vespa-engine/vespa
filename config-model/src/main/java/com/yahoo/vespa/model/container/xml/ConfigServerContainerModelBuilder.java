// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.ConnectionLogComponent;
import com.yahoo.vespa.model.container.configserver.ConfigserverCluster;
import com.yahoo.vespa.model.container.configserver.option.ConfigOptions;
import org.w3c.dom.Element;

/**
 * Builds the config model for the standalone config server.
 *
 * @author Ulf Lilleengen
 */
public class ConfigServerContainerModelBuilder extends ContainerModelBuilder {

    private final ConfigOptions options;

    public ConfigServerContainerModelBuilder(ConfigOptions options) {
        super(true, Networking.enable);
        this.options = options;
    }

    @Override
    public void doBuild(ContainerModel model, Element spec, ConfigModelContext modelContext) {
        ConfigserverCluster cluster = new ConfigserverCluster(modelContext.getParentProducer(), "configserver",
                                                              options, modelContext.featureFlags());
        super.doBuild(model, spec, modelContext.withParent(cluster));
        cluster.setContainerCluster(model.getCluster());
    }

    // Need to override this method since we need to use the values in ConfigOptions (the ones
    // in ConfigModelContext.DeployState.properties are not set)
    @Override
    protected void addStatusHandlers(ApplicationContainerCluster cluster, boolean isHostedVespa) {
        super.addStatusHandlers(cluster, isHosted());
    }

    // Override access log configuration for hosted configserver/controller
    @Override
    protected void addAccessLogs(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        if (isHosted()){
            cluster.addAccessLog("logs/vespa/configserver/access-json.log.%Y%m%d%H%M%S", "access-json.log");
            cluster.addComponent(new ConnectionLogComponent(cluster, FileConnectionLog.class, "configserver"));
        } else {
            super.addAccessLogs(deployState, cluster, spec);
        }
    }

    @Override
    protected void addModelEvaluationRuntime(ApplicationContainerCluster cluster) {
        // Model evaluation bundles are pre-installed in the standalone container.
    }

    /** Note: using {@link ConfigOptions} as {@link DeployState#isHosted()} returns <em>false</em> for hosted configserver/controller */
    private boolean isHosted() { return options.hostedVespa().orElse(Boolean.FALSE); }
}

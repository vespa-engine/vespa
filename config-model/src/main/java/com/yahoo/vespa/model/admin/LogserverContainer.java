// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.AccessLogComponent.AccessLogType;
import java.util.Optional;

/**
 * Container that should be running on same host as the logserver. Sets up a handler for getting logs from logserver.
 * Only in use in hosted Vespa.
 */
public class LogserverContainer extends Container {

    public LogserverContainer(TreeConfigProducer<?> parent, DeployState deployState) {
        super(parent, "" + 0, 0, deployState);
        if (deployState.isHosted() && deployState.getProperties().applicationId().instance().isTester()) useDynamicPorts();
        LogserverContainerCluster cluster = (LogserverContainerCluster) parent;
        addComponent(new AccessLogComponent(cluster, AccessLogType.jsonAccessLog,
                                            deployState.featureFlags().logFileCompressionAlgorithm("zstd"),
                                            Optional.of(cluster.getName()), true));
    }

    @Override
    public ContainerServiceType myServiceType() {
        return ContainerServiceType.LOGSERVER_CONTAINER;
    }

    @Override
    public String defaultPreload() {
        return "";
    }

    @Override
    protected String jvmOmitStackTraceInFastThrowOption(ModelContext.FeatureFlags featureFlags) {
        return featureFlags.jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type.admin);
    }

}

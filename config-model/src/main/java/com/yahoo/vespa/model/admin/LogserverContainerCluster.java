// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hmusum
 */
public class LogserverContainerCluster extends ContainerCluster<LogserverContainer> {

    public LogserverContainerCluster(TreeConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState, true);

        addDefaultHandlersWithVip();
        addLogHandler();
        setJvmGCOptions(deployState.getProperties().jvmGCOptions(Optional.of(ClusterSpec.Type.admin)));
        if (isHostedVespa())
            addAccessLog(getName());
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);
        builder.jvm.heapsize(128);
    }

    protected boolean messageBusEnabled() { return false; }

    private void addLogHandler() {
        Handler logHandler = Handler.fromClassName(ContainerCluster.LOG_HANDLER_CLASS);
        logHandler.addServerBindings(SystemBindingPattern.fromHttpPath("/logs"));
        addComponent(logHandler);
    }

    @Override
    protected Set<Path> unnecessaryPlatformBundles() {
        return Stream.concat(PlatformBundles.VESPA_SECURITY_BUNDLES.stream(),
                             PlatformBundles.VESPA_ZK_BUNDLES.stream())
                .collect(Collectors.toSet());
    }

}

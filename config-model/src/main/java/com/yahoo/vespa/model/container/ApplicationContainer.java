// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import java.time.Duration;
import java.util.Optional;

/**
 * A container that is typically used by container clusters set up from the user application.
 *
 * @author gjoranv
 */
public final class ApplicationContainer extends Container implements
        QrStartConfig.Producer,
        OnnxModelsConfig.Producer,
        ZookeeperServerConfig.Producer {

    private final boolean isHostedVespa;

    public ApplicationContainer(TreeConfigProducer<?> parent, String name, int index, DeployState deployState) {
        this(parent, name, false, index, deployState);
    }

    public ApplicationContainer(TreeConfigProducer<?> parent, String name, boolean retired, int index, DeployState deployState) {
        super(parent, name, retired, index, deployState);
        this.isHostedVespa = deployState.isHosted();

        addComponent(new SimpleComponent("com.yahoo.container.jdisc.messagebus.NetworkMultiplexerHolder"));
        addComponent(new SimpleComponent("com.yahoo.container.jdisc.messagebus.NetworkMultiplexerProvider"));
        addComponent(new SimpleComponent("com.yahoo.container.jdisc.messagebus.SessionCache"));
        addComponent(new SimpleComponent("com.yahoo.container.jdisc.SystemInfoProvider"));
        addComponent(new SimpleComponent("com.yahoo.container.jdisc.ZoneInfoProvider"));
        addComponent(new SimpleComponent("com.yahoo.container.jdisc.ClusterInfoProvider"));
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        realResources().ifPresent(r -> builder.jvm.availableProcessors(Math.max(2, (int) Math.ceil(r.vcpu()))));
    }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) {
        realResources().ifPresent(r -> {
            int count = r.gpuResources().count();
            if (count >= 0) builder.gpu.count(count);
        });
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return ContainerServiceType.CONTAINER;
    }

    /** Returns the jvm arguments this should start with */
    @Override
    public String getJvmOptions() {
        StringBuilder b = new StringBuilder();
        if (isHostedVespa) {
            b.append("-Djdk.tls.server.enableStatusRequestExtension=true ")
                    .append("-Djdk.tls.stapling.responseTimeout=2000 ")
                    .append("-Djdk.tls.stapling.cacheSize=256 ")
                    .append("-Djdk.tls.stapling.cacheLifetime=3600 ");
        }
        String jvmArgs = super.getJvmOptions();
        if (!jvmArgs.isBlank()) {
             b.append(jvmArgs.trim());
        }
        return b.toString().trim();
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.myid(index());
    }

    @Override
    protected String jvmOmitStackTraceInFastThrowOption(ModelContext.FeatureFlags featureFlags) {
        return featureFlags.jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type.container);
    }

    @Override public Optional<String> getPreShutdownCommand() { return Optional.of(prepareStopCommand(Duration.ofMinutes(6))); }

    private Optional<NodeResources> realResources() {
        if (getHostResource() != null) {
            NodeResources nodeResources = getHostResource().realResources();
            if ( ! nodeResources.isUnspecified()) {
                return Optional.of(nodeResources);
            }
        }
        return Optional.empty();
    }

}

// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.container.component.Component;

/**
 * A container that is typically used by container clusters set up from the user application.
 *
 * @author gjoranv
 */
public final class ApplicationContainer extends Container implements
        QrStartConfig.Producer,
        ZookeeperServerConfig.Producer {

    private static final String defaultHostedJVMArgs = "-XX:+UseOSErrorReporting -XX:+SuppressFatalErrorMessage";

    private final boolean isHostedVespa;

    public ApplicationContainer(AbstractConfigProducer<?> parent, String name, int index, boolean isHostedVespa) {
        this(parent, name, false, index, isHostedVespa);
    }

    public ApplicationContainer(AbstractConfigProducer<?> parent, String name, boolean retired, int index, boolean isHostedVespa) {
        super(parent, name, retired, index, isHostedVespa);
        this.isHostedVespa = isHostedVespa;

        addComponent(getFS4ResourcePool()); // TODO Remove when FS4 based search protocol is gone
    }

    private static Component<?, ComponentModel> getFS4ResourcePool() {
        BundleInstantiationSpecification spec = BundleInstantiationSpecification.
                getInternalSearcherSpecificationFromStrings(FS4ResourcePool.class.getName(), null);
        return new Component<>(new ComponentModel(spec));
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        if (getHostResource() != null) {
            NodeResources nodeResources = getHostResource().realResources();
            if ( ! nodeResources.isUnspecified()) {
                builder.jvm.availableProcessors(Math.max(2, (int)Math.ceil(nodeResources.vcpu())));
            }
        }
    }

    @Override
    protected ContainerServiceType myServiceType() {
        if (parent instanceof ContainerCluster) {
            ContainerCluster<?> cluster = (ContainerCluster<?>)parent;
            // TODO: The 'qrserver' name is retained for legacy reasons (e.g. system tests and log parsing).
            if (cluster.getSearch() != null && cluster.getDocproc() == null && cluster.getDocumentApi() == null) {
                return ContainerServiceType.QRSERVER;
            }
        }
        return ContainerServiceType.CONTAINER;
    }

    /** Returns the jvm arguments this should start with */
    @Override
    public String getJvmOptions() {
        String jvmArgs = super.getJvmOptions();
        return isHostedVespa && hasDocproc()
                ? ("".equals(jvmArgs) ? defaultHostedJVMArgs : defaultHostedJVMArgs + " " + jvmArgs)
                : jvmArgs;
    }

    private boolean hasDocproc() {
        return (parent instanceof ContainerCluster) && (((ContainerCluster<?>)parent).getDocproc() != null);
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.myid(index());
    }

}

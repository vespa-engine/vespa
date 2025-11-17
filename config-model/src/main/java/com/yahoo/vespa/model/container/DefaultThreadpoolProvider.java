// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

/**
 * Component definition for the jdisc default threadpool provider ({@link ThreadPoolProvider}).
 *
 * @author bjorncs
 */
public class DefaultThreadpoolProvider extends SimpleComponent implements ThreadpoolConfig.Producer {

    private final ContainerCluster<?> cluster;
    private final ContainerThreadpool.UserOptions userOptions;

    public DefaultThreadpoolProvider(ContainerCluster<?> cluster) {
        this(cluster, null);
    }

    public DefaultThreadpoolProvider(ContainerCluster<?> cluster, ContainerThreadpool.UserOptions userOptions) {
        super(new ComponentModel(
                BundleInstantiationSpecification.fromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
        this.userOptions = userOptions;
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        int maxThreads;
        int minThreads;
        int queueSize;

        // Default values
        if (cluster instanceof ApplicationContainerCluster) {
            // Core pool size of 2xcores, and max of 100xcores and using a synchronous Q
            // This is the default pool used by both federation and generally when you ask for an Executor.
            maxThreads = -100;
            minThreads = -2;
            queueSize = 0;
        } else {
            // Container clusters such as logserver, metricsproxy and clustercontroller
            maxThreads = 4;
            minThreads = 4;
            queueSize = 50;
        }

        // Convert from config model to ThreadpoolConfig.
        if (userOptions != null) {
            boolean neg = userOptions.isRelative();
            if (userOptions.max() != null) {
                maxThreads = neg ? -userOptions.max().intValue() : userOptions.max().intValue();
            }
            if (userOptions.min() != null) {
                minThreads = neg ? -userOptions.min().intValue() : userOptions.min().intValue();
            }
            if (userOptions.queueSize() != null) {
                queueSize = neg ? -userOptions.queueSize().intValue() : userOptions.queueSize().intValue();
            }
        }

        builder.corePoolSize(minThreads).maxthreads(maxThreads).queueSize(queueSize);
    }
}

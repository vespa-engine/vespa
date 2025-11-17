// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.logging.Level;

/**
 * Component definition for the jdisc default threadpool provider ({@link ThreadPoolProvider}).
 *
 * @author bjorncs
 */
public class DefaultThreadpoolProvider extends SimpleComponent implements ThreadpoolConfig.Producer {

    private final ContainerCluster<?> cluster;
    private final ContainerThreadpool.UserOptions userOptions;

    public DefaultThreadpoolProvider(ContainerCluster<?> cluster) {
        this(null, cluster, null);
    }

    public DefaultThreadpoolProvider(DeployState ds, ContainerCluster<?> cluster, ContainerThreadpool.UserOptions userOptions) {
        super(new ComponentModel(
                BundleInstantiationSpecification.fromStrings(
                        "default-threadpool",
                        ThreadPoolProvider.class.getName(),
                        null)));
        this.cluster = cluster;
        this.userOptions = userOptions;

        if (this.userOptions != null) {
            warnOnTruncation(ds, userOptions);
        }
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
        // TODO: While ThreadpoolConfig exists, we must support this conversion since it uses ints. Will be removed in Vespa 9.
        if (userOptions != null) {
            boolean neg = userOptions.isRelative();
            if (userOptions.max() != null) {
                maxThreads = (int) Math.round(neg ? -userOptions.max() : userOptions.max());
            }
            if (userOptions.min() != null) {
                minThreads = (int) Math.round(neg ? -userOptions.min() : userOptions.min());
            }
            if (userOptions.queueSize() != null) {
                queueSize = (int) Math.round(neg ? -userOptions.queueSize() : userOptions.queueSize());
            }
        }

        builder.corePoolSize(minThreads).maxthreads(maxThreads).queueSize(queueSize);
    }

    // TODO: While ThreadpoolConfig exists, we must support this conversion. Will be removed in Vespa 9.
    private void warnOnTruncation(DeployState ds, ContainerThreadpool.UserOptions userOptions) {
        if (userOptions.max() != null) checkTruncation(ds, userOptions.max(), "max");
        if (userOptions.min() != null) checkTruncation(ds, userOptions.min(), "threads");
        if (userOptions.queueSize() != null) checkTruncation(ds, userOptions.queueSize(), "queue");
    }

    private void checkTruncation(DeployState ds, Double value, String part) {
        double truncated = (double) Math.round(value);
        if (truncated != value) {
            ds.getDeployLogger()
                    .logApplicationPackage(Level.WARNING,
                            "For <threadpool> in <container>: the value " +
                                    part + "=" + value + " will be truncated to " + truncated
                                    + ". This will be removed in Vespa 9");
        }
    }
}

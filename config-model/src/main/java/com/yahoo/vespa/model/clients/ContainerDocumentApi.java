// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ThreadPoolExecutorComponent;
import com.yahoo.vespa.model.container.component.Handler;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApi {

    private static final int FALLBACK_MAX_POOL_SIZE = 0; // Use fallback based on actual logical core count on host
    private static final int FALLBACK_CORE_POOL_SIZE = 0; // Use fallback based on actual logical core count on host

    public ContainerDocumentApi(ContainerCluster<?> cluster, Options options) {
        setupHandlers(cluster, options);
    }

    private static void setupHandlers(ContainerCluster<?> cluster, Options options) {
        addRestApiHandler(cluster, options);
        addFeedHandler(cluster, options);
    }

    private static void addFeedHandler(ContainerCluster<?> cluster, Options options) {
        var executorComponent = newExecutorComponent("feedapi-handler", cluster, options);
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var handler = newVespaClientHandler(
                "com.yahoo.vespa.http.server.FeedHandler", bindingSuffix, options, executorComponent);
        cluster.addComponent(handler);
    }

    private static void addRestApiHandler(ContainerCluster<?> cluster, Options options) {
        var executorComponent = newExecutorComponent("restapi-handler", cluster, options);
        var handler = newVespaClientHandler(
                "com.yahoo.document.restapi.resource.RestApi", "document/v1/*", options, executorComponent);
        cluster.addComponent(handler);
    }

    private static ThreadPoolExecutorComponent newExecutorComponent(String name, ContainerCluster<?> cluster, Options options) {
        int maxPoolSize = maxPoolSize(cluster);
        return new ThreadPoolExecutorComponent.Builder(name)
                .maxPoolSize(maxPoolSize)
                .corePoolSize(corePoolSize(maxPoolSize, options))
                .queueSize(500)
                .build();
    }

    private static Handler<AbstractConfigProducer<?>> newVespaClientHandler(
            String componentId,
            String bindingSuffix,
            Options options,
            ThreadPoolExecutorComponent executorComponent) {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentId, null, "vespaclient-container-plugin"), ""));
        for (String rootBinding : options.bindings) {
            handler.addServerBindings(rootBinding + bindingSuffix, rootBinding + bindingSuffix + '/');
        }
        handler.addComponent(executorComponent);
        return handler;
    }

    private static int maxPoolSize(ContainerCluster<?> cluster) {
        List<Double> vcpus = cluster.getContainers().stream()
                .map(c -> c.getHostResource().realResources().vcpu())
                .distinct()
                .collect(Collectors.toList());
        // We can only use host resource for calculation if all container nodes in the cluster are homogeneous (in terms of vcpu)
        if (vcpus.size() != 1 || vcpus.get(0) == 0) return FALLBACK_MAX_POOL_SIZE;
        return (int)Math.ceil(vcpus.get(0));
    }

    private static int corePoolSize(int maxPoolSize, Options options) {
        if (maxPoolSize == FALLBACK_MAX_POOL_SIZE) return FALLBACK_CORE_POOL_SIZE;
        return (int) Math.ceil(options.feedCoreThreadPoolSizeFactor * maxPoolSize);
    }

    public static final class Options {
        private final Collection<String> bindings;
        private final double feedCoreThreadPoolSizeFactor;

        public Options(Collection<String> bindings, double feedCoreThreadPoolSizeFactor) {
            this.bindings = Collections.unmodifiableCollection(bindings);
            this.feedCoreThreadPoolSizeFactor = feedCoreThreadPoolSizeFactor;
        }
    }

}

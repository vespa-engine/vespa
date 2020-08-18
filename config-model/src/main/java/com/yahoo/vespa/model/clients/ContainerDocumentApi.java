// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ThreadPoolExecutorComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;

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

    private final ContainerCluster<?> cluster;
    private final Options options;
    private final Handler<AbstractConfigProducer<?>> feedHandler;
    private final Handler<AbstractConfigProducer<?>> restApiHandler;

    public ContainerDocumentApi(ContainerCluster<?> cluster, Options options) {
        this.cluster = cluster;
        this.options = options;
        this.restApiHandler = addRestApiHandler(cluster, options);
        this.feedHandler = addFeedHandler(cluster, options);
    }


    public void addNodesDependentThreadpoolConfiguration() {
        if (cluster.getContainers().isEmpty()) throw new IllegalStateException("Cluster is empty");
        ThreadPoolExecutorComponent feedHandlerExecutor = newExecutorComponent("feedapi-handler", cluster, options);
        feedHandler.inject(feedHandlerExecutor);
        feedHandler.addComponent(feedHandlerExecutor);
        ThreadPoolExecutorComponent restApiHandlerExecutor = newExecutorComponent("restapi-handler", cluster, options);
        restApiHandler.inject(restApiHandlerExecutor);
        restApiHandler.addComponent(restApiHandlerExecutor);
    }

    private static Handler<AbstractConfigProducer<?>> addFeedHandler(ContainerCluster<?> cluster, Options options) {
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var handler = newVespaClientHandler(
                "com.yahoo.vespa.http.server.FeedHandler", bindingSuffix, options);
        cluster.addComponent(handler);
        return handler;
    }


    private static Handler<AbstractConfigProducer<?>> addRestApiHandler(ContainerCluster<?> cluster, Options options) {
        var handler = newVespaClientHandler(
                "com.yahoo.document.restapi.resource.RestApi", "/document/v1/*", options);
        cluster.addComponent(handler);
        return handler;
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
            Options options) {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentId, null, "vespaclient-container-plugin"), ""));
        if (options.bindings.isEmpty()) {
            handler.addServerBindings(
                    SystemBindingPattern.fromHttpPath(bindingSuffix),
                    SystemBindingPattern.fromHttpPath(bindingSuffix + '/'));
        } else {
            for (String rootBinding : options.bindings) {
                String pathWithoutLeadingSlash = bindingSuffix.substring(1);
                handler.addServerBindings(
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash),
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash + '/'));
            }
        }
        return handler;
    }

    private static int maxPoolSize(ContainerCluster<?> cluster) {
        List<Double> vcpus = cluster.getContainers().stream()
                .filter(c -> c.getHostResource() != null && c.getHostResource().realResources() != null)
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

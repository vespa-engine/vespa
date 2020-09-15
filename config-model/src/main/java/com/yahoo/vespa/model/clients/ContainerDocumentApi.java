// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpoolComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApi {

    private static final int FALLBACK_MAX_POOL_SIZE = 0; // Use fallback based on actual logical core count on host
    private static final int FALLBACK_CORE_POOL_SIZE = 0; // Use fallback based on actual logical core count on host

    public ContainerDocumentApi(ContainerCluster<?> cluster, Options options) {
        addRestApiHandler(cluster, options);
        addFeedHandler(cluster, options);
    }

    private static void addFeedHandler(ContainerCluster<?> cluster, Options options) {
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var handler = newVespaClientHandler(
                "com.yahoo.vespa.http.server.FeedHandler", bindingSuffix, options);
        cluster.addComponent(handler);
        var executor = new ThreadpoolComponent("feedapi-handler", cluster, options);
        handler.inject(executor);
        handler.addComponent(executor);
    }


    private static void addRestApiHandler(ContainerCluster<?> cluster, Options options) {
        var handler = newVespaClientHandler(
                "com.yahoo.document.restapi.resource.RestApi", "/document/v1/*", options);
        cluster.addComponent(handler);
        var executor = new ThreadpoolComponent("restapi-handler", cluster, options);
        handler.inject(executor);
        handler.addComponent(executor);
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

    public static final class Options {
        private final Collection<String> bindings;
        private final double feedThreadPoolSizeFactor;

        public Options(Collection<String> bindings, double feedThreadPoolSizeFactor) {
            this.bindings = Collections.unmodifiableCollection(bindings);
            this.feedThreadPoolSizeFactor = feedThreadPoolSizeFactor;
        }
    }

    private static class ThreadpoolComponent extends ContainerThreadpoolComponent {

        private final ContainerCluster<?> cluster;
        private final Options options;

        ThreadpoolComponent(String name, ContainerCluster<?> cluster, Options options) {
            super(name);
            this.cluster = cluster;
            this.options = options;
        }

        @Override
        public void getConfig(ContainerThreadpoolConfig.Builder builder) {
            super.getConfig(builder);
            builder.maxThreads(maxPoolSize(cluster, options));
            builder.minThreads(minPoolSize(cluster, options));
            builder.queueSize(500);
        }

        private static int maxPoolSize(ContainerCluster<?> cluster, Options options) {
            double vcpu = vcpu(cluster);
            if (vcpu == 0) return FALLBACK_MAX_POOL_SIZE;
            return Math.max(2, (int)Math.ceil(vcpu * options.feedThreadPoolSizeFactor));
        }

        private static int minPoolSize(ContainerCluster<?> cluster, Options options) {
            double vcpu = vcpu(cluster);
            if (vcpu == 0) return FALLBACK_CORE_POOL_SIZE;
            return Math.max(1, (int)Math.ceil(vcpu * options.feedThreadPoolSizeFactor * 0.5));
        }
    }

}

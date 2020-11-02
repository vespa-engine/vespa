// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
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
        var executor = new Threadpool(
                "feedapi-handler", cluster, options.feedApiThreadpoolOptions);
        handler.inject(executor);
        handler.addComponent(executor);
    }


    private static void addRestApiHandler(ContainerCluster<?> cluster, Options options) {
        // TODO(bjorncs,jonmv) Cleanup once old restapi handler is gone
        // We need to include the old handler implementation even when the new handler is enabled
        // The internal legacy test framework requires that the name of the old handler is listed in /ApplicationStatus
        String oldHandlerName = "com.yahoo.document.restapi.resource.RestApi";
        String bindingSuffix = "/document/v1/*";
        var oldHandler = newVespaClientHandler(oldHandlerName, options.useNewRestapiHandler ? null : bindingSuffix, options);
        cluster.addComponent(oldHandler);
        var executor = new Threadpool("restapi-handler", cluster, options.restApiThreadpoolOptions);
        oldHandler.inject(executor);
        oldHandler.addComponent(executor);

        if (options.useNewRestapiHandler) {
            String newHandlerName = "com.yahoo.document.restapi.resource.DocumentV1ApiHandler";
            var newHandler = newVespaClientHandler(newHandlerName, bindingSuffix, options);
            cluster.addComponent(newHandler);
        }
    }

    private static Handler<AbstractConfigProducer<?>> newVespaClientHandler(
            String componentId,
            String bindingSuffix,
            Options options) {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(componentId, null, "vespaclient-container-plugin"), ""));
        if (bindingSuffix == null) return handler; // TODO(bjorncs,jonmv) Cleanup once old restapi handler is gone
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
        private final ContainerThreadpool.UserOptions restApiThreadpoolOptions;
        private final ContainerThreadpool.UserOptions feedApiThreadpoolOptions;
        private final boolean useNewRestapiHandler;

        public Options(Collection<String> bindings,
                       ContainerThreadpool.UserOptions restApiThreadpoolOptions,
                       ContainerThreadpool.UserOptions feedApiThreadpoolOptions,
                       boolean useNewRestapiHandler) {
            this.bindings = Collections.unmodifiableCollection(bindings);
            this.restApiThreadpoolOptions = restApiThreadpoolOptions;
            this.feedApiThreadpoolOptions = feedApiThreadpoolOptions;
            this.useNewRestapiHandler = useNewRestapiHandler;
        }
    }

    private static class Threadpool extends ContainerThreadpool {

        private final ContainerCluster<?> cluster;

        Threadpool(String name,
                   ContainerCluster<?> cluster,
                   ContainerThreadpool.UserOptions threadpoolOptions) {
            super(name, threadpoolOptions);
            this.cluster = cluster;
        }

        @Override
        public void getConfig(ContainerThreadpoolConfig.Builder builder) {
            super.getConfig(builder);

            // User options overrides below configuration
            if (hasUserOptions()) return;

            builder.maxThreads(maxPoolSize());
            builder.minThreads(minPoolSize());
            builder.queueSize(500);
        }

        private int maxPoolSize() {
            double vcpu = cluster.vcpu().orElse(0);
            if (vcpu == 0) return FALLBACK_MAX_POOL_SIZE;
            return Math.max(2, (int)Math.ceil(vcpu * 4.0));
        }

        private int minPoolSize() {
            double vcpu = cluster.vcpu().orElse(0);
            if (vcpu == 0) return FALLBACK_CORE_POOL_SIZE;
            return Math.max(1, (int)Math.ceil(vcpu * 2.0));
        }
    }

}

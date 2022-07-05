// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public static final String DOCUMENT_V1_PREFIX = "/document/v1";

    private final boolean ignoreUndefinedFields;

    public ContainerDocumentApi(ContainerCluster<?> cluster, HandlerOptions handlerOptions, boolean ignoreUndefinedFields) {
        this.ignoreUndefinedFields = ignoreUndefinedFields;
        addRestApiHandler(cluster, handlerOptions);
        addFeedHandler(cluster, handlerOptions);
    }

    private static void addFeedHandler(ContainerCluster<?> cluster, HandlerOptions handlerOptions) {
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var handler = newVespaClientHandler("com.yahoo.vespa.http.server.FeedHandler", bindingSuffix, handlerOptions);
        cluster.addComponent(handler);
        var executor = new Threadpool("feedapi-handler", handlerOptions.feedApiThreadpoolOptions);
        handler.inject(executor);
        handler.addComponent(executor);
    }


    private static void addRestApiHandler(ContainerCluster<?> cluster, HandlerOptions handlerOptions) {
        var handler = newVespaClientHandler("com.yahoo.document.restapi.resource.DocumentV1ApiHandler", DOCUMENT_V1_PREFIX + "/*", handlerOptions);
        cluster.addComponent(handler);

        // We need to include a dummy implementation of the previous restapi handler (using the same class name).
        // The internal legacy test framework requires that the name of the old handler is listed in /ApplicationStatus.
        var oldHandlerDummy = handlerComponentSpecification("com.yahoo.document.restapi.resource.RestApi");
        cluster.addComponent(oldHandlerDummy);
    }

    public boolean ignoreUndefinedFields() { return ignoreUndefinedFields; }

    private static Handler<AbstractConfigProducer<?>> newVespaClientHandler(
            String componentId,
            String bindingSuffix,
            HandlerOptions handlerOptions) {
        Handler<AbstractConfigProducer<?>> handler = handlerComponentSpecification(componentId);
        if (handlerOptions.bindings.isEmpty()) {
            handler.addServerBindings(
                    SystemBindingPattern.fromHttpPath(bindingSuffix),
                    SystemBindingPattern.fromHttpPath(bindingSuffix + '/'));
        } else {
            for (String rootBinding : handlerOptions.bindings) {
                String pathWithoutLeadingSlash = bindingSuffix.substring(1);
                handler.addServerBindings(
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash),
                        UserBindingPattern.fromPattern(rootBinding + pathWithoutLeadingSlash + '/'));
            }
        }
        return handler;
    }

    private static Handler<AbstractConfigProducer<?>> handlerComponentSpecification(String className) {
        return new Handler<>(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(className, null, "vespaclient-container-plugin"), ""));
    }

    public static final class HandlerOptions {

        private final Collection<String> bindings;
        private final ContainerThreadpool.UserOptions feedApiThreadpoolOptions;

        public HandlerOptions(Collection<String> bindings, ContainerThreadpool.UserOptions feedApiThreadpoolOptions) {
            this.bindings = Collections.unmodifiableCollection(bindings);
            this.feedApiThreadpoolOptions = feedApiThreadpoolOptions;
        }
    }

    private static class Threadpool extends ContainerThreadpool {

        Threadpool(String name, ContainerThreadpool.UserOptions threadpoolOptions) {
            super(name, threadpoolOptions);
        }

        @Override
        public void getConfig(ContainerThreadpoolConfig.Builder builder) {
            super.getConfig(builder);

            // User options overrides below configuration
            if (hasUserOptions()) return;
            builder.maxThreads(-4).minThreads(-4).queueSize(500);
        }
    }

}

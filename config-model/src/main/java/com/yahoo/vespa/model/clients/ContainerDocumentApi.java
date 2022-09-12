// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalInt;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApi {

    public static final String DOCUMENT_V1_PREFIX = "/document/v1";
    public static final Path VESPACLIENT_CONTAINER_BUNDLE =
            PlatformBundles.absoluteBundlePath("vespaclient-container-plugin");

    private final boolean ignoreUndefinedFields;

    public ContainerDocumentApi(ContainerCluster<?> cluster, HandlerOptions handlerOptions, boolean ignoreUndefinedFields, OptionalInt portOverride) {
        this.ignoreUndefinedFields = ignoreUndefinedFields;
        addRestApiHandler(cluster, handlerOptions, portOverride);
        addFeedHandler(cluster, handlerOptions, portOverride);
        addVespaClientContainerBundle(cluster);
    }

    public static void addVespaClientContainerBundle(ContainerCluster<?> c) {
        c.addPlatformBundle(VESPACLIENT_CONTAINER_BUNDLE);
    }

    private static void addFeedHandler(ContainerCluster<?> cluster, HandlerOptions handlerOptions, OptionalInt portOverride) {
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var executor = new Threadpool("feedapi-handler", handlerOptions.feedApiThreadpoolOptions);
        var handler = newVespaClientHandler("com.yahoo.vespa.http.server.FeedHandler",
                                            bindingSuffix, handlerOptions, executor, portOverride);
        cluster.addComponent(handler);
    }


    private static void addRestApiHandler(ContainerCluster<?> cluster, HandlerOptions handlerOptions, OptionalInt portOverride) {
        var handler = newVespaClientHandler("com.yahoo.document.restapi.resource.DocumentV1ApiHandler",
                                            DOCUMENT_V1_PREFIX + "/*", handlerOptions, null, portOverride);
        cluster.addComponent(handler);

        // We need to include a dummy implementation of the previous restapi handler (using the same class name).
        // The internal legacy test framework requires that the name of the old handler is listed in /ApplicationStatus.
        var oldHandlerDummy = createHandler("com.yahoo.document.restapi.resource.RestApi", null);
        cluster.addComponent(oldHandlerDummy);
    }

    public boolean ignoreUndefinedFields() { return ignoreUndefinedFields; }

    private static Handler newVespaClientHandler(String componentId,
                                                 String bindingSuffix,
                                                 HandlerOptions handlerOptions,
                                                 Threadpool executor,
                                                 OptionalInt portOverride) {
        Handler handler = createHandler(componentId, executor);
        if (handlerOptions.bindings.isEmpty()) {
            handler.addServerBindings(
                    bindingPattern(bindingSuffix, portOverride),
                    bindingPattern(bindingSuffix + '/', portOverride));
        } else {
            for (String rootBinding : handlerOptions.bindings) {
                String pathWithoutLeadingSlash = bindingSuffix.substring(1);
                handler.addServerBindings(
                        userBindingPattern(rootBinding + pathWithoutLeadingSlash, portOverride),
                        userBindingPattern(rootBinding + pathWithoutLeadingSlash + '/', portOverride));
            }
        }
        return handler;
    }

    private static BindingPattern bindingPattern(String path, OptionalInt port) {
        return port.isPresent()
                ? SystemBindingPattern.fromHttpPortAndPath(Integer.toString(port.getAsInt()), path)
                : SystemBindingPattern.fromHttpPath(path);
    }

    private static UserBindingPattern userBindingPattern(String path, OptionalInt port) {
        UserBindingPattern bindingPattern = UserBindingPattern.fromPattern(path);
        return port.isPresent()
                ? bindingPattern.withPort(port.getAsInt())
                : bindingPattern;
    }

    private static Handler createHandler(String className, Threadpool executor) {
        return new Handler(new ComponentModel(className, null, "vespaclient-container-plugin"),
                           executor);
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
        protected void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            builder.maxThreads(-4)
                    .minThreads(-4)
                    .queueSize(500);
        }
    }

}

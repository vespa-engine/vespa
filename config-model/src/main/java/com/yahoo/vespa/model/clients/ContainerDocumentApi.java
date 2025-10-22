// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.xml.DocumentApiOptionsBuilder;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ContainerDocumentApi {

    public static final String DOCUMENT_V1_PREFIX = "/document/v1";
    public static final Path VESPACLIENT_CONTAINER_BUNDLE =
            PlatformBundles.absoluteBundlePath("vespaclient-container-plugin");

    private final boolean ignoreUndefinedFields;

    public ContainerDocumentApi(DeployState ds, ContainerCluster<?> cluster, HandlerOptions handlerOptions,
                                boolean ignoreUndefinedFields, Set<Integer> portOverride) {
        this.ignoreUndefinedFields = ignoreUndefinedFields;
        addRestApiHandler(cluster, handlerOptions, portOverride);
        addFeedHandler(ds, cluster, handlerOptions, portOverride);
        addVespaClientContainerBundle(cluster);
    }

    // Used for creating dummy document api that will be used when document api is not configured
    private ContainerDocumentApi(ContainerCluster<?> cluster) {
        this.ignoreUndefinedFields = true;
        var handlerOptions = DocumentApiOptionsBuilder.build(null);
        var handler = newVespaClientHandler("com.yahoo.document.restapi.resource.DummyDocumentV1ApiHandler",
                                            DOCUMENT_V1_PREFIX + "/*", handlerOptions, null, Set.of());
        cluster.addComponent(handler);
        addVespaClientContainerBundle(cluster);
    }

    public static ContainerDocumentApi createDummyApi(ContainerCluster<?> cluster) {
        return System.getProperty("vespa.local", "false").equals("true") // set by Application when running locally
                ? null
                : new ContainerDocumentApi(cluster);
    }

    public static void addVespaClientContainerBundle(ContainerCluster<?> c) {
        c.addPlatformBundle(VESPACLIENT_CONTAINER_BUNDLE);
    }

    private static void addFeedHandler(DeployState ds, ContainerCluster<?> cluster, HandlerOptions handlerOptions, Set<Integer> portOverride) {
        String bindingSuffix = ContainerCluster.RESERVED_URI_PREFIX + "/feedapi";
        var executor = new Threadpool(ds, "feedapi-handler", handlerOptions.feedApiThreadpoolOptions);
        var handler = newVespaClientHandler("com.yahoo.vespa.http.server.FeedHandler",
                                            bindingSuffix, handlerOptions, executor, portOverride);
        cluster.addComponent(handler);
    }


    private static void addRestApiHandler(ContainerCluster<?> cluster, HandlerOptions handlerOptions, Set<Integer> portOverride) {
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
                                                 Set<Integer> portOverride) {
        Handler handler = createHandler(componentId, executor);
        if (handlerOptions.bindings.isEmpty()) {
            handler.addServerBindings(bindingPattern(bindingSuffix, portOverride));
            handler.addServerBindings(bindingPattern(bindingSuffix + '/', portOverride));
        } else {
            for (String rootBinding : handlerOptions.bindings) {
                String pathWithoutLeadingSlash = bindingSuffix.substring(1);
                handler.addServerBindings(userBindingPattern(rootBinding + pathWithoutLeadingSlash, portOverride));
                handler.addServerBindings(userBindingPattern(rootBinding + pathWithoutLeadingSlash + '/', portOverride));
            }
        }
        return handler;
    }

    private static List<BindingPattern> bindingPattern(String path, Set<Integer> ports) {
        if (ports.isEmpty()) return List.of(SystemBindingPattern.fromHttpPath(path));
        return ports.stream()
                .map(p -> (BindingPattern)SystemBindingPattern.fromHttpPortAndPath(p, path))
                .toList();
    }

    private static List<BindingPattern> userBindingPattern(String path, Set<Integer> ports) {
        UserBindingPattern bindingPattern = UserBindingPattern.fromPattern(path);
        if (ports.isEmpty()) return List.of(bindingPattern);
        return ports.stream()
                .map(p -> (BindingPattern)bindingPattern.withOverriddenPort(p))
                .toList();
    }

    private static Handler createHandler(String className, Threadpool executor) {
        return new Handler(new ComponentModel(className, null, "vespaclient-container-plugin"),
                           executor);
    }

    public static final class HandlerOptions {

        private final Collection<String> bindings;
        private final Element feedApiThreadpoolOptions;

        public HandlerOptions(Collection<String> bindings, Element feedApiThreadpoolOptions) {
            this.bindings = Collections.unmodifiableCollection(bindings);
            this.feedApiThreadpoolOptions = feedApiThreadpoolOptions;
        }
    }

    private static class Threadpool extends ContainerThreadpool {

        Threadpool(DeployState ds, String name, Element xml) { super(ds, name, xml); }

        @Override
        protected void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            builder.maxThreads(-4)
                    .minThreads(-4)
                    .queueSize(500);
        }
    }

}

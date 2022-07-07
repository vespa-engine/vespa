// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.ContainerThreadpool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Models a jdisc RequestHandler (including ClientProvider).
 * RequestHandlers always have at least one server binding,
 * while ClientProviders have at least one client binding.
 * <p>
 * Note that this is also used to model vespa handlers (which do not have any bindings)
 *
 * @author gjoranv
 */
public class Handler<CHILD extends AbstractConfigProducer<?>> extends Component<CHILD, ComponentModel> {

    private final Set<BindingPattern> serverBindings = new LinkedHashSet<>();
    private final List<BindingPattern> clientBindings = new ArrayList<>();

    public final boolean hasCustomThreadPool;

    public Handler(ComponentModel model) {
        this(model, null);
    }

    @SuppressWarnings("unchecked")
    public Handler(ComponentModel model, ContainerThreadpool threadpool) {
        super(model);

        // The default threadpool is always added to the cluster, so cannot be added here.
        if (threadpool != null) {
            hasCustomThreadPool = true;
            addComponent((CHILD) threadpool);
            inject(threadpool);
        } else {
            hasCustomThreadPool = false;
        }
    }

    public static Handler<AbstractConfigProducer<?>> fromClassName(String className) {
        return new Handler<>(new ComponentModel(className, null, null, null));
    }

    public void addServerBindings(BindingPattern... bindings) {
        serverBindings.addAll(Arrays.asList(bindings));
    }

    public void removeServerBinding(BindingPattern binding) {
        serverBindings.remove(binding);
    }

    public void addClientBindings(BindingPattern... bindings) {
        clientBindings.addAll(Arrays.asList(bindings));
    }

    public final Set<BindingPattern> getServerBindings() {
        return Collections.unmodifiableSet(serverBindings);
    }

    public final List<BindingPattern> getClientBindings() {
        return Collections.unmodifiableList(clientBindings);
    }


    /**
     * The default threadpool for all handlers, except those that declare their own, e.g. SearchHandler.
     */
    public static class DefaultHandlerThreadpool extends ContainerThreadpool {

        public DefaultHandlerThreadpool() {
            super("default-handler-common", null);
        }

        @Override
        public void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder) {
            builder.maxThreadExecutionTimeSeconds(190)
                    .keepAliveTime(5.0)
                    .maxThreads(-2)
                    .minThreads(-2)
                    .queueSize(-40);
        }
    }

}

// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for http access control.
 *
 * @author gjoranv
 * @author bjorncs
 */
public class AccessControl {

    public static final ComponentId ACCESS_CONTROL_CHAIN_ID = ComponentId.fromString("access-control-chain");
    public static final ComponentId ACCESS_CONTROL_EXCLUDED_CHAIN_ID = ComponentId.fromString("access-control-excluded-chain");

    private static final int HOSTED_CONTAINER_PORT = 4443;

    // Handlers that are excluded from access control
    private static final List<String> EXCLUDED_HANDLERS = List.of(
            FileStatusHandlerComponent.CLASS,
            ContainerCluster.APPLICATION_STATUS_HANDLER_CLASS,
            ContainerCluster.BINDINGS_OVERVIEW_HANDLER_CLASS,
            ContainerCluster.STATE_HANDLER_CLASS,
            ContainerCluster.LOG_HANDLER_CLASS,
            ApplicationContainerCluster.METRICS_V2_HANDLER_CLASS,
            ApplicationContainerCluster.PROMETHEUS_V1_HANDLER_CLASS
    );

    public static class Builder {
        private final String domain;
        private boolean readEnabled = false;
        private boolean writeEnabled = true;
        private final Set<BindingPattern> excludeBindings = new LinkedHashSet<>();
        private Collection<Handler<?>> handlers = Collections.emptyList();

        public Builder(String domain) {
            this.domain = domain;
        }

        public Builder readEnabled(boolean readEnabled) {
            this.readEnabled = readEnabled;
            return this;
        }

        public Builder writeEnabled(boolean writeEnabled) {
            this.writeEnabled = writeEnabled;
            return this;
        }

        public Builder excludeBinding(BindingPattern binding) {
            this.excludeBindings.add(binding);
            return this;
        }

        public Builder setHandlers(ApplicationContainerCluster cluster) {
            this.handlers = cluster.getHandlers();
            return this;
        }

        public AccessControl build() {
            return new AccessControl(domain, writeEnabled, readEnabled, excludeBindings, handlers);
        }
    }

    public final String domain;
    public final boolean readEnabled;
    public final boolean writeEnabled;
    private final Set<BindingPattern> excludedBindings;
    private final Collection<Handler<?>> handlers;

    private AccessControl(String domain,
                          boolean writeEnabled,
                          boolean readEnabled,
                          Set<BindingPattern> excludedBindings,
                          Collection<Handler<?>> handlers) {
        this.domain = domain;
        this.readEnabled = readEnabled;
        this.writeEnabled = writeEnabled;
        this.excludedBindings = Collections.unmodifiableSet(excludedBindings);
        this.handlers = handlers;
    }

    public void configure(Http http) {
        http.setAccessControl(this);
        addAccessControlFilterChain(http);
        addAccessControlExcludedChain(http);
    }

    public static boolean hasHandlerThatNeedsProtection(ApplicationContainerCluster cluster) {
        return cluster.getHandlers().stream()
                .anyMatch(handler -> ! isExcludedHandler(handler) && hasNonMbusBinding(handler));
    }

    private void addAccessControlFilterChain(Http http) {
        http.getFilterChains().add(createChain(ACCESS_CONTROL_CHAIN_ID));
        http.getBindings().addAll(List.of(createAccessControlBinding("/"), createAccessControlBinding("/*")));
    }

    private void addAccessControlExcludedChain(Http http) {
        Chain<Filter> chain = createChain(ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
        chain.addInnerComponent(
                new Filter(
                        new ChainedComponentModel(
                                new BundleInstantiationSpecification(
                                        new ComponentSpecification("com.yahoo.jdisc.http.filter.security.misc.NoopFilter"),
                                        null,
                                        new ComponentSpecification("jdisc-security-filters")),
                                Dependencies.emptyDependencies())));
        http.getFilterChains().add(chain);
        for (BindingPattern excludedBinding : excludedBindings) {
            http.getBindings().add(createAccessControlExcludedBinding(excludedBinding));
        }
        for (Handler<?> handler : handlers) {
            if (isExcludedHandler(handler)) {
                for (BindingPattern binding : handler.getServerBindings()) {
                    http.getBindings().add(createAccessControlExcludedBinding(binding));
                }
            }
        }
    }

    private static FilterBinding createAccessControlBinding(String path) {
        return FilterBinding.create(
                new ComponentSpecification(ACCESS_CONTROL_CHAIN_ID.stringValue()),
                SystemBindingPattern.fromPortAndPath(Integer.toString(HOSTED_CONTAINER_PORT), path));
    }

    private static FilterBinding createAccessControlExcludedBinding(BindingPattern excludedBinding) {
        BindingPattern rewrittenBinding = SystemBindingPattern.fromPortAndPath(
                Integer.toString(HOSTED_CONTAINER_PORT), excludedBinding.path()); // only keep path from excluded binding
        return FilterBinding.create(
                new ComponentSpecification(ACCESS_CONTROL_EXCLUDED_CHAIN_ID.stringValue()),
                rewrittenBinding);
    }

    private static Chain<Filter> createChain(ComponentId id) { return new Chain<>(FilterChains.emptyChainSpec(id)); }

    private static boolean isExcludedHandler(Handler<?> handler) { return EXCLUDED_HANDLERS.contains(handler.getClassId().getName()); }

    private static boolean hasNonMbusBinding(Handler<?> handler) {
        return handler.getServerBindings().stream().anyMatch(binding -> ! binding.scheme().equals("mbus"));
    }

}

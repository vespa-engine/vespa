// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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



    public enum ClientAuthentication { want, need;}
    public static final ComponentId ACCESS_CONTROL_CHAIN_ID = ComponentId.fromString("access-control-chain");

    public static final ComponentId ACCESS_CONTROL_EXCLUDED_CHAIN_ID = ComponentId.fromString("access-control-excluded-chain");
    public static final ComponentId DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID = ComponentId.fromString("default-connector-hosted-request-chain");
    private static final int HOSTED_CONTAINER_PORT = 4443;

    // Handlers that are excluded from access control
    public static final List<String> EXCLUDED_HANDLERS = List.of(
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
        private ClientAuthentication clientAuthentication = ClientAuthentication.need;
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

        public Builder clientAuthentication(ClientAuthentication clientAuthentication) {
            this.clientAuthentication = clientAuthentication;
            return this;
        }

        public AccessControl build() {
            return new AccessControl(domain, writeEnabled, readEnabled, clientAuthentication, excludeBindings, handlers);
        }
    }

    public final String domain;
    public final boolean readEnabled;
    public final boolean writeEnabled;
    public final ClientAuthentication clientAuthentication;
    private final Set<BindingPattern> excludedBindings;
    private final Collection<Handler<?>> handlers;

    private AccessControl(String domain,
                          boolean writeEnabled,
                          boolean readEnabled,
                          ClientAuthentication clientAuthentication,
                          Set<BindingPattern> excludedBindings,
                          Collection<Handler<?>> handlers) {
        this.domain = domain;
        this.readEnabled = readEnabled;
        this.writeEnabled = writeEnabled;
        this.clientAuthentication = clientAuthentication;
        this.excludedBindings = Collections.unmodifiableSet(excludedBindings);
        this.handlers = handlers;
    }

    public void configureHttpFilterChains(Http http) {
        http.setAccessControl(this);
        addAccessControlFilterChain(http);
        addAccessControlExcludedChain(http);
        addDefaultHostedRequestChain(http);
        removeDuplicateBindingsFromAccessControlChain(http);
    }

    public void configureHostedConnector(HostedSslConnectorFactory connectorFactory) {
        connectorFactory.setDefaultRequestFilterChain(ACCESS_CONTROL_CHAIN_ID);
    }

    public void configureDefaultHostedConnector(Http http) {
        // Set default filter chain on local port
        http.getHttpServer()
                .get()
                .getConnectorFactories()
                .stream()
                .filter(cf -> cf.getListenPort() == Defaults.getDefaults().vespaWebServicePort())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find default connector"))
                .setDefaultRequestFilterChain(DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID);
    }

    /** returns the excluded bindings as specified in 'access-control' in services.xml **/
    public Set<BindingPattern> excludedBindings() { return excludedBindings; }

    /** all handlers (that are known by the access control components) **/
    public Collection<Handler<?>> handlers() { return handlers; }

    public static boolean hasHandlerThatNeedsProtection(ApplicationContainerCluster cluster) {
        return cluster.getHandlers().stream()
                .anyMatch(handler -> ! isExcludedHandler(handler) && hasNonMbusBinding(handler));
    }

    private void addAccessControlFilterChain(Http http) {
        http.getFilterChains().add(createChain(ACCESS_CONTROL_CHAIN_ID));
    }

    private void addAccessControlExcludedChain(Http http) {
        http.getFilterChains().add(createChain(ACCESS_CONTROL_EXCLUDED_CHAIN_ID));
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

    // Add a filter chain used by default hosted connector
    private void addDefaultHostedRequestChain(Http http) {
        Chain<Filter> chain = createChain(DEFAULT_CONNECTOR_HOSTED_REQUEST_CHAIN_ID);
        http.getFilterChains().add(chain);
    }

    // Remove bindings from access control chain that have binding pattern as a different filter chain
    private void removeDuplicateBindingsFromAccessControlChain(Http http) {
        removeDuplicateBindingsFromChain(http, ACCESS_CONTROL_EXCLUDED_CHAIN_ID);
    }

    private void removeDuplicateBindingsFromChain(Http http, ComponentId chainId) {
        Set<FilterBinding> duplicateBindings = new HashSet<>();
        for (FilterBinding binding : http.getBindings()) {
            if (binding.chainId().toId().equals(chainId)) {
                for (FilterBinding otherBinding : http.getBindings()) {
                    if (effectivelyDuplicateOf(binding, otherBinding)) {
                        duplicateBindings.add(binding);
                    }
                }
            }
        }
        duplicateBindings.forEach(http.getBindings()::remove);
    }

    private static boolean effectivelyDuplicateOf(FilterBinding accessControlBinding, FilterBinding other) {
        if (accessControlBinding.chainId().equals(other.chainId())) return false; // Same filter chain
        if (other.type() == FilterBinding.Type.RESPONSE) return false;
        return accessControlBinding.binding().equals(other.binding())
                || (accessControlBinding.binding().path().equals(other.binding().path()) && other.binding().matchesAnyPort());
    }


    private static FilterBinding createAccessControlExcludedBinding(BindingPattern excludedBinding) {
        BindingPattern rewrittenBinding = SystemBindingPattern.fromHttpPortAndPath(
                Integer.toString(HOSTED_CONTAINER_PORT), excludedBinding.path()); // only keep path from excluded binding
        return FilterBinding.create(
                FilterBinding.Type.REQUEST,
                new ComponentSpecification(ACCESS_CONTROL_EXCLUDED_CHAIN_ID.stringValue()),
                rewrittenBinding);
    }

    private static Chain<Filter> createChain(ComponentId id) { return new Chain<>(FilterChains.emptyChainSpec(id)); }

    private static boolean isExcludedHandler(Handler<?> handler) { return EXCLUDED_HANDLERS.contains(handler.getClassId().getName()); }

    private static boolean hasNonMbusBinding(Handler<?> handler) {
        return handler.getServerBindings().stream().anyMatch(binding -> ! binding.scheme().equals("mbus"));
    }

}

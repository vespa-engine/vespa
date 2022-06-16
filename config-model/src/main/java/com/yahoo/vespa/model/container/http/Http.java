// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents the http servers and filters of a container cluster.
 *
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class Http extends AbstractConfigProducer<AbstractConfigProducer<?>> implements ServerConfig.Producer {

    private final FilterChains filterChains;
    private final List<FilterBinding> bindings = new CopyOnWriteArrayList<>();
    private volatile JettyHttpServer httpServer;
    private volatile AccessControl accessControl;
    private volatile Boolean strictFiltering;

    public Http(FilterChains chains) {
        super("http");
        this.filterChains = chains;
    }

    public void setAccessControl(AccessControl accessControl) {
            if (this.accessControl != null) throw new IllegalStateException("Access control already assigned");
            this.accessControl = accessControl;
    }

    public FilterChains getFilterChains() {
        return filterChains;
    }

    public Optional<JettyHttpServer> getHttpServer() {
        return Optional.ofNullable(httpServer);
    }

    public void setHttpServer(JettyHttpServer newServer) {
        JettyHttpServer oldServer = this.httpServer;
        this.httpServer = newServer;

        if (oldServer == null && newServer != null) {
            addChild(newServer);
        } else if (newServer == null && oldServer != null) {
            removeChild(oldServer);
        } else if (newServer == null && oldServer == null) {
            //do nothing
        } else {
            //none of them are null
            removeChild(oldServer);
            addChild(newServer);
        }
    }

    public void removeAllServers() {
        setHttpServer(null);
    }

    public List<FilterBinding> getBindings() {
        return bindings;
    }

    public Optional<AccessControl> getAccessControl() {
        return Optional.ofNullable(accessControl);
    }

    public void setStrictFiltering(boolean enabled) { this.strictFiltering = enabled; }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
        for (FilterBinding binding : bindings) {
            builder.filter(new ServerConfig.Filter.Builder()
                    .id(binding.chainId().stringValue())
                    .binding(binding.binding().patternString()));
        }
        populateDefaultFiltersConfig(builder, httpServer);

        // Enable strict filter by default if any filter chain/binding is configured
        boolean strictFilter = this.strictFiltering == null
                ? (!bindings.isEmpty() || !filterChains.allChains().allComponents().isEmpty())
                : strictFiltering;
        builder.strictFiltering(strictFilter);
    }

    @Override
    public void validate() {
        if (((Collection<FilterBinding>) bindings).isEmpty()) return;

        if (filterChains == null)
            throw new IllegalArgumentException("Null FilterChains are not allowed when there are filter bindings");

        ComponentRegistry<ChainedComponent<?>> filters = filterChains.componentsRegistry();
        ComponentRegistry<Chain<Filter>> chains = filterChains.allChains();

        for (FilterBinding binding: bindings) {
            if (filters.getComponent(binding.chainId()) == null && chains.getComponent(binding.chainId()) == null)
                throw new IllegalArgumentException("Can't find filter " + binding.chainId() + " for binding " + binding.binding());
        }
    }

    private static void populateDefaultFiltersConfig(ServerConfig.Builder builder, JettyHttpServer httpServer) {
        if (httpServer != null) {
            for (ConnectorFactory connector : httpServer.getConnectorFactories()) {
                connector.getDefaultRequestFilterChain().ifPresent(
                        filterChain -> builder.defaultFilters(new ServerConfig.DefaultFilters.Builder()
                                .filterId(filterChain.stringValue())
                                .localPort(connector.getListenPort())));
                connector.getDefaultResponseFilterChain().ifPresent(
                        filterChain -> builder.defaultFilters(new ServerConfig.DefaultFilters.Builder()
                                .filterId(filterChain.stringValue())
                                .localPort(connector.getListenPort())));
            }
        }
    }
}

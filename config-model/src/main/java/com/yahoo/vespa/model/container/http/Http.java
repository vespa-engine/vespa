// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.jdisc.config.HttpServerConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.vespa.model.container.component.chain.Chain;
import com.yahoo.vespa.model.container.component.chain.ChainedComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents the http servers and filters of a Jdisc cluster.
 *
 * @author Tony Vaagenes
 */
public class Http extends AbstractConfigProducer<AbstractConfigProducer<?>>
        implements HttpServerConfig.Producer, ServerConfig.Producer {

    public static class Binding {
        public final ComponentSpecification filterId;
        public final String binding;

        public Binding(ComponentSpecification filterId, String binding) {
            this.filterId = filterId;
            this.binding = binding;
        }
    }

    private FilterChains filterChains;
    private JettyHttpServer httpServer;
    public final List<Binding> bindings;
    private final Optional<AccessControl> accessControl;

    public Http(List<Binding> bindings) {
        this(bindings, null);
    }

    public Http(List<Binding> bindings, AccessControl accessControl) {
        super( "http");
        this.bindings = Collections.unmodifiableList(bindings);
        this.accessControl = Optional.ofNullable(accessControl);
    }

    public void setFilterChains(FilterChains filterChains) {
        this.filterChains = filterChains;
    }

    public FilterChains getFilterChains() {
        return filterChains;
    }

    public JettyHttpServer getHttpServer() {
        return httpServer;
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

    public List<Binding> getBindings() {
        return bindings;
    }

    public Optional<AccessControl> getAccessControl() {
        return accessControl;
    }

    @Override
    public void getConfig(HttpServerConfig.Builder builder) {
        for (Binding binding: bindings)
            builder.filter(filterBindings(binding));
    }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
        for (final Binding binding : bindings) {
            builder.filter(
                    new ServerConfig.Filter.Builder()
                            .id(binding.filterId.stringValue())
                            .binding(binding.binding));
        }
    }

    static HttpServerConfig.Filter.Builder filterBindings(Binding binding) {
        HttpServerConfig.Filter.Builder builder = new HttpServerConfig.Filter.Builder();
        builder.id(binding.filterId.stringValue()).
                binding(binding.binding);
        return builder;
    }


    @Override
    public void validate() throws Exception {
        validate(bindings);
    }

    void validate(Collection<Binding> bindings) {
        if (!bindings.isEmpty()) {
            if (filterChains == null)
                throw new IllegalArgumentException("Null FilterChains is not allowed when there are filter bindings!");

            ComponentRegistry<ChainedComponent<?>> filters = filterChains.componentsRegistry();
            ComponentRegistry<Chain<Filter>> chains = filterChains.allChains();

            for (Binding binding: bindings) {
                if (filters.getComponent(binding.filterId) == null && chains.getComponent(binding.filterId) == null)
                    throw new RuntimeException("Can't find filter " + binding.filterId + " for binding " + binding.binding);
            }
        }
    }

}

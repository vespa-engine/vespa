// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final Object monitor = new Object();

    private final FilterChains filterChains;
    private final List<Binding> bindings = new CopyOnWriteArrayList<>();
    private JettyHttpServer httpServer;
    private AccessControl accessControl;

    public Http(FilterChains chains) {
        super("http");
        this.filterChains = chains;
    }

    public void setAccessControl(AccessControl accessControl) {
        synchronized (monitor) {
            if (this.accessControl != null) throw new IllegalStateException("Access control already assigned");
            this.accessControl = accessControl;
        }
    }

    public FilterChains getFilterChains() {
        synchronized (monitor) {
            return filterChains;
        }
    }

    public Optional<JettyHttpServer> getHttpServer() {
        synchronized (monitor) {
            return Optional.ofNullable(httpServer);
        }
    }

    public void setHttpServer(JettyHttpServer newServer) {
        synchronized (monitor) {
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
    }

    public void removeAllServers() {
        synchronized (monitor) {
            setHttpServer(null);
        }
    }

    public List<Binding> getBindings() {
        synchronized (monitor) {
            return bindings;
        }
    }

    public Optional<AccessControl> getAccessControl() {
        synchronized (monitor) {
            return Optional.ofNullable(accessControl);
        }
    }

    @Override
    public void getConfig(ServerConfig.Builder builder) {
        synchronized (monitor) {
            for (Binding binding : bindings) {
                builder.filter(new ServerConfig.Filter.Builder()
                                       .id(binding.filterId().stringValue())
                                       .binding(binding.binding()));
            }
        }
    }

    @Override
    public void validate() {
        synchronized (monitor) {
            if (((Collection<Binding>) bindings).isEmpty()) return;

            if (filterChains == null)
                throw new IllegalArgumentException("Null FilterChains are not allowed when there are filter bindings");

            ComponentRegistry<ChainedComponent<?>> filters = filterChains.componentsRegistry();
            ComponentRegistry<Chain<Filter>> chains = filterChains.allChains();

            for (Binding binding: bindings) {
                if (filters.getComponent(binding.filterId()) == null && chains.getComponent(binding.filterId()) == null)
                    throw new RuntimeException("Can't find filter " + binding.filterId() + " for binding " + binding.binding());
            }
        }
    }
}

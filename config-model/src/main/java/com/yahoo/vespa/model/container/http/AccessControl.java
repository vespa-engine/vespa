// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.Servlet;
import com.yahoo.vespa.model.container.http.Http.Binding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for http access control.
 *
 * @author gjoranv
 */
public final class AccessControl {

    public static final ComponentId ACCESS_CONTROL_CHAIN_ID = ComponentId.fromString("access-control-chain");

    private static final List<String> UNPROTECTED_HANDLERS = ImmutableList.of(
            FileStatusHandlerComponent.CLASS,
            ContainerCluster.APPLICATION_STATUS_HANDLER_CLASS,
            ContainerCluster.BINDINGS_OVERVIEW_HANDLER_CLASS,
            ContainerCluster.STATE_HANDLER_CLASS,
            ContainerCluster.STATISTICS_HANDLER_CLASS
    );

    public static final class Builder {
        private String domain;
        private String applicationId;
        private Optional<String> vespaDomain = Optional.empty();
        private boolean readEnabled = false;
        private boolean writeEnabled = true;
        private final Set<String> excludeBindings = new LinkedHashSet<>();
        private Collection<Handler<?>> handlers = Collections.emptyList();
        private Collection<Servlet> servlets = Collections.emptyList();

        public Builder(String domain, String applicationId) {
            this.domain = domain;
            this.applicationId = applicationId;
        }

        public Builder readEnabled(boolean readEnabled) {
            this.readEnabled = readEnabled;
            return this;
        }

        public Builder writeEnabled(boolean writeEnalbed) {
            this.writeEnabled = writeEnalbed;
            return this;
        }

        public Builder excludeBinding(String binding) {
            this.excludeBindings.add(binding);
            return this;
        }

        public Builder vespaDomain(String vespaDomain) {
            this.vespaDomain = Optional.ofNullable(vespaDomain);
            return this;
        }

        public Builder setHandlers(Collection<Handler<?>> handlers) {
            this.handlers = handlers;
            return this;
        }

        public Builder setServlets(Collection<Servlet> servlets) {
            this.servlets = servlets;
            return this;
        }

        public AccessControl build() {
            return new AccessControl(domain, applicationId, writeEnabled, readEnabled,
                                     excludeBindings, vespaDomain, servlets, handlers);
        }
    }

    public final String domain;
    public final String applicationId;
    public final boolean readEnabled;
    public final boolean writeEnabled;
    public final Optional<String> vespaDomain;
    private final Set<String> excludedBindings;
    private final Collection<Handler<?>> handlers;
    private final Collection<Servlet> servlets;

    private AccessControl(String domain,
                          String applicationId,
                          boolean writeEnabled,
                          boolean readEnabled,
                          Set<String> excludedBindings,
                          Optional<String> vespaDomain,
                          Collection<Servlet> servlets,
                          Collection<Handler<?>> handlers) {
        this.domain = domain;
        this.applicationId = applicationId;
        this.readEnabled = readEnabled;
        this.writeEnabled = writeEnabled;
        this.excludedBindings = Collections.unmodifiableSet(excludedBindings);
        this.vespaDomain = vespaDomain;
        this.handlers = handlers;
        this.servlets = servlets;
    }

    public List<Binding> getBindings() {
        return Stream.concat(getHandlerBindings(), getServletBindings())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Stream<Binding> getHandlerBindings() {
        return handlers.stream()
                        .filter(this::shouldHandlerBeProtected)
                        .flatMap(handler -> handler.getServerBindings().stream())
                        .map(AccessControl::accessControlBinding);
    }

    private Stream<Binding> getServletBindings() {
        return servlets.stream()
                .filter(this::shouldServletBeProtected)
                .flatMap(AccessControl::servletBindings)
                .map(AccessControl::accessControlBinding);
    }

    private boolean shouldHandlerBeProtected(Handler<?> handler) {
        return ! isBuiltinGetOnly(handler)
                && handler.getServerBindings().stream().noneMatch(excludedBindings::contains);
    }

    public static boolean isBuiltinGetOnly(Handler<?> handler) {
        return UNPROTECTED_HANDLERS.contains(handler.getClassId().getName());
    }

    private boolean shouldServletBeProtected(Servlet servlet) {
        return servletBindings(servlet).noneMatch(excludedBindings::contains);
    }

    private static Binding accessControlBinding(String binding) {
        return new Binding(new ComponentSpecification(ACCESS_CONTROL_CHAIN_ID.stringValue()), binding);
    }

    private static Stream<String> servletBindings(Servlet servlet) {
        return Stream.of("http://*/", "https://*/").map(protocol -> protocol + servlet.bindingPath);
    }
}

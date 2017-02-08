// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.http.Http.Binding;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        private String vespaDomain = "";
        private boolean readEnabled = false;
        private boolean writeEnabled = true;
        private final Set<String> excludeBindings = new LinkedHashSet<>();

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
            this.vespaDomain = vespaDomain;
            return this;
        }

        public AccessControl build() {
            return new AccessControl(domain, applicationId, writeEnabled, readEnabled, excludeBindings, vespaDomain);
        }
    }

    public final String domain;
    public final String applicationId;
    public final boolean readEnabled;
    public final boolean writeEnabled;
    public final Set<String> excludedBindings;
    public final String vespaDomain;

    private AccessControl(String domain,
                          String applicationId,
                          boolean writeEnabled,
                          boolean readEnabled,
                          Set<String> excludedBindings,
                          String vespaDomain) {
        this.domain = domain;
        this.applicationId = applicationId;
        this.readEnabled = readEnabled;
        this.writeEnabled = writeEnabled;
        this.excludedBindings = Collections.unmodifiableSet(excludedBindings);
        this.vespaDomain = vespaDomain;
    }

    public boolean shouldHandlerBeProtected(Handler<?> handler) {
        return ! UNPROTECTED_HANDLERS.contains(handler.getClassId().getName())
                && handler.getServerBindings().stream().noneMatch(excludedBindings::contains);
    }

    public static Binding accessControlBinding(String binding) {
        return new Binding(new ComponentSpecification(ACCESS_CONTROL_CHAIN_ID.stringValue()), binding);
    }

}

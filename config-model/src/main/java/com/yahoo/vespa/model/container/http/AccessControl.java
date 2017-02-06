// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.http.Http.Binding;

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

    private final Set<String> excludedBindings;

    public AccessControl(Set<String> excludedBindings) {
        this.excludedBindings = excludedBindings;
    }

    public boolean shouldHandlerBeProtected(Handler<?> handler) {
        return ! UNPROTECTED_HANDLERS.contains(handler.getClassId().getName())
                && handler.getServerBindings().stream().noneMatch(excludedBindings::contains);
    }

    public static Binding accessControlBinding(String binding) {
        return new Binding(new ComponentSpecification(ACCESS_CONTROL_CHAIN_ID.stringValue()), binding);
    }

}

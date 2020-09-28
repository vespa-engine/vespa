// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.List;

/**
 * Represents an action to restart services in order to handle a config change.
 *
 * @author geirst
 */
public class VespaRestartAction extends VespaConfigChangeAction implements ConfigChangeRestartAction {

    private final boolean ignoreForInternalRedeploy;

    public VespaRestartAction(String message) {
        this(message, List.of());
    }

    public VespaRestartAction(String message, ServiceInfo service) {
        this(message, List.of(service));
    }

    public VespaRestartAction(String message, ServiceInfo services, boolean ignoreForInternalRedeploy) {
        super(message, List.of(services));
        this.ignoreForInternalRedeploy = ignoreForInternalRedeploy;
    }

    public VespaRestartAction(String message, List<ServiceInfo> services) {
        super(message, services);
        this.ignoreForInternalRedeploy = false;
    }

    @Override
    public VespaConfigChangeAction modifyAction(String newMessage, List<ServiceInfo> newServices, String documentType) {
        return new VespaRestartAction(newMessage, newServices);
    }

    @Override
    public boolean ignoreForInternalRedeploy() {
        return ignoreForInternalRedeploy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        VespaRestartAction that = (VespaRestartAction) o;
        return ignoreForInternalRedeploy == that.ignoreForInternalRedeploy;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (ignoreForInternalRedeploy ? 1 : 0);
        return result;
    }
}

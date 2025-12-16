// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeRestartAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;

import java.util.List;

/**
 * Represents an action to restart services in order to handle a config change.
 *
 * @author geirst
 */
public class VespaRestartAction extends VespaConfigChangeAction implements ConfigChangeRestartAction {

    public enum ConfigChange { IMMEDIATE, DEFER_UNTIL_RESTART }

    private final boolean ignoreForInternalRedeploy;
    private final ConfigChange configChange;

    /** <strong>This does <em>not</em> trigger restarts; you <em>need</em> the {@code ServiceInfo}!</strong>*/
    public VespaRestartAction(ClusterSpec.Id id, String message) {
        this(id, message, List.of());
    }

    public VespaRestartAction(ClusterSpec.Id id, String message, ServiceInfo service) {
        this(id, message, List.of(service));
    }

    public VespaRestartAction(ClusterSpec.Id id, String message, ServiceInfo services, boolean ignoreForInternalRedeploy) {
        super(id, message, List.of(services));
        this.ignoreForInternalRedeploy = ignoreForInternalRedeploy;
        this.configChange = ConfigChange.IMMEDIATE;
    }

    public VespaRestartAction(ClusterSpec.Id id, String message, List<ServiceInfo> services) {
        super(id, message, services);
        this.ignoreForInternalRedeploy = false;
        this.configChange = ConfigChange.IMMEDIATE;
    }

    public VespaRestartAction(ClusterSpec.Id id, String message, List<ServiceInfo> services, ConfigChange configChange) {
        super(id, message, services);
        this.ignoreForInternalRedeploy = false;
        this.configChange = configChange;
    }

    public VespaRestartAction(ClusterSpec.Id id, String message, List<ServiceInfo> services,
                             boolean ignoreForInternalRedeploy, ConfigChange configChange) {
        super(id, message, services);
        this.ignoreForInternalRedeploy = ignoreForInternalRedeploy;
        this.configChange = configChange;
    }

    @Override
    public VespaConfigChangeAction modifyAction(String newMessage, List<ServiceInfo> newServices, String documentType) {
        return new VespaRestartAction(clusterId(), newMessage, newServices);
    }

    @Override
    public boolean ignoreForInternalRedeploy() {
        return ignoreForInternalRedeploy;
    }

    public ConfigChange configChange() {
        return configChange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        VespaRestartAction that = (VespaRestartAction) o;
        return ignoreForInternalRedeploy == that.ignoreForInternalRedeploy
            && configChange == that.configChange;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (ignoreForInternalRedeploy ? 1 : 0);
        result = 31 * result + configChange.hashCode();
        return result;
    }
}

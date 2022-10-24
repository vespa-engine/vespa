// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Class containing the action to be performed on the given services to handle a config change
 * between the current active vespa model and the next vespa model to prepare.
 *
 * @author geirst
 */
public abstract class VespaConfigChangeAction implements ConfigChangeAction {

    private final ClusterSpec.Id id;
    private final String message;
    private final List<ServiceInfo> services;

    protected VespaConfigChangeAction(ClusterSpec.Id id, String message, List<ServiceInfo> services) {
        this.id = id;
        this.message = message;
        this.services = services;
    }

    public abstract VespaConfigChangeAction modifyAction(String newMessage, List<ServiceInfo> newServices, String documentType);

    @Override
    public ClusterSpec.Id clusterId() { return id; }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<ServiceInfo> getServices() {
        return services;
    }

    @Override
    public String toString() {
        return "type='" + getType() + "', message='" + message + "', services=[" +
                services.stream().
                        map(service -> service.getServiceName() + " '" + service.getConfigId() + "'").
                        collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VespaConfigChangeAction)) {
            return false;
        }
        VespaConfigChangeAction rhs = (VespaConfigChangeAction)o;
        if (!getType().equals(rhs.getType())) return false;
        if (!message.equals(rhs.message)) return false;
        if (!services.equals(rhs.services)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = getType().hashCode();
        result = 31 * result + message.hashCode();
        result = 31 * result + services.hashCode();
        return result;
    }

}

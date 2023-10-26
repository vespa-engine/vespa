// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;

/**
 * Contains information about a service.
 *
 * @author Ulf Lilleengen
 */
public class ServiceInfo {

    private final String serviceName;
    private final String serviceType;
    private final Collection<PortInfo> ports;
    private final Map<String, String> properties;
    private final String configId;
    private final String hostName;

    public ServiceInfo(String serviceName, String serviceType, Collection<PortInfo> ports, Map<String, String> properties,
                       String configId, String hostName) {

        Objects.requireNonNull(configId);

        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.ports = ports;
        this.properties = properties;
        this.configId = configId;
        this.hostName = hostName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getConfigId() {
        return configId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public Optional<String> getProperty(String propertyName) {
        return Optional.ofNullable(properties.get(propertyName));
    }

    public Collection<PortInfo> getPorts() {
        return ports;
    }

    public String getHostName() {
        return hostName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceInfo that = (ServiceInfo) o;

        if (ports != null ? !ports.equals(that.ports) : that.ports != null) return false;
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) return false;
        if (!serviceName.equals(that.serviceName)) return false;
        if (!serviceType.equals(that.serviceType)) return false;
        if (!configId.equals(that.configId)) return false;
        if (!hostName.equals(that.hostName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = serviceName.hashCode();
        result = 31 * result + serviceType.hashCode();
        result = 31 * result + (ports != null ? ports.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        result = 31 * result + configId.hashCode();
        result = 31 * result + hostName.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "service '" + serviceName + "' of type " + serviceType + " on " + hostName;
    }

}

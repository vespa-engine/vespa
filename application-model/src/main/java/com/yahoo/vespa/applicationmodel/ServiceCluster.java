// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Represents a collection of service instances that together make up a service with a single cluster id.
 *
 * @author bjorncs
 */
public class ServiceCluster {

    private final ClusterId clusterId;
    private final ServiceType serviceType;
    private final Set<ServiceInstance> serviceInstances;
    private Optional<ApplicationInstance> applicationInstance = Optional.empty();

    public ServiceCluster(ClusterId clusterId, ServiceType serviceType, Set<ServiceInstance> serviceInstances) {
        this.clusterId = clusterId;
        this.serviceType = serviceType;
        this.serviceInstances = serviceInstances;
    }

    @JsonProperty("clusterId")
    public ClusterId clusterId() {
        return clusterId;
    }

    @JsonProperty("serviceType")
    public ServiceType serviceType() {
        return serviceType;
    }

    @JsonProperty("serviceInstances")
    public Set<ServiceInstance> serviceInstances() {
        return serviceInstances;
    }

    @JsonIgnore
    public void setApplicationInstance(ApplicationInstance applicationInstance) {
        this.applicationInstance = Optional.of(applicationInstance);
    }

    @JsonIgnore
    public ApplicationInstance getApplicationInstance() {
        return applicationInstance.get();
    }

    @JsonIgnore
    public boolean isConfigServerLike() {
        return isConfigServer() || isController();
    }

    @JsonIgnore
    public boolean isController() {
        return isHostedVespaApplicationWithId(ApplicationInstanceId.CONTROLLER) &&
                Objects.equals(clusterId, ClusterId.CONTROLLER) &&
                Objects.equals(serviceType, ServiceType.CONTROLLER);
    }

    /** Is a config server (and not controller!) */
    @JsonIgnore
    public boolean isConfigServer() {
        return isHostedVespaApplicationWithId(ApplicationInstanceId.CONFIG_SERVER) &&
                Objects.equals(clusterId, ClusterId.CONFIG_SERVER) &&
                Objects.equals(serviceType, ServiceType.CONFIG_SERVER);
    }

    @JsonIgnore
    public boolean isConfigServerHost() {
        return isHostedVespaApplicationWithPredicate(ApplicationInstanceId::isConfigServerHost) &&
                Objects.equals(clusterId, ClusterId.CONFIG_SERVER_HOST) &&
                Objects.equals(serviceType, ServiceType.HOST_ADMIN);
    }

    @JsonIgnore
    public boolean isControllerHost() {
        return isHostedVespaApplicationWithId(ApplicationInstanceId.CONTROLLER_HOST) &&
                Objects.equals(clusterId, ClusterId.CONTROLLER_HOST) &&
                Objects.equals(serviceType, ServiceType.HOST_ADMIN);
    }

    @JsonIgnore
    public boolean isConfigServerHostLike() {
        return isConfigServerHost() || isControllerHost();
    }

    @JsonIgnore
    public boolean isTenantHost() {
        return isHostedVespaApplicationWithPredicate(ApplicationInstanceId::isTenantHost) &&
                Objects.equals(clusterId, ClusterId.TENANT_HOST) &&
                Objects.equals(serviceType, ServiceType.HOST_ADMIN);
    }

    @JsonIgnore
    public boolean isProxy() {
        return isHostedVespaApplicationWithPredicate(ApplicationInstanceId::isProxy) &&
               Objects.equals(clusterId, ClusterId.ROUTING) &&
               Objects.equals(serviceType, ServiceType.CONTAINER);
    }

    @JsonIgnore
    public boolean isProxyHost() {
        return isHostedVespaApplicationWithPredicate(ApplicationInstanceId::isProxyHost) &&
               Objects.equals(clusterId, ClusterId.PROXY_HOST) &&
               Objects.equals(serviceType, ServiceType.HOST_ADMIN);
    }

    public String nodeDescription(boolean plural) {
        return entityDescription("node", plural);
    }

    public String serviceDescription(boolean plural) {
        return entityDescription("service", plural);
    }

    private String entityDescription(String entity, boolean plural) {
        String pluralSuffix = plural ? "s" : "";
        return isConfigServer() ? "config server" + pluralSuffix :
               isConfigServerHost() ? "config server host" + pluralSuffix :
               isController() ? "controller" + pluralSuffix :
               isControllerHost() ? "controller host" + pluralSuffix :
               isProxy() ? (plural ? "proxies" : "proxy") :
               isProxyHost() ? "proxy host" + pluralSuffix :
               isTenantHost() ? "tenant host" + pluralSuffix :
               entity + pluralSuffix + " of {" + serviceType + "," + clusterId + "}";
    }

    private boolean isHostedVespaApplicationWithId(ApplicationInstanceId id) {
        return isHostedVespaTenant() &&
               applicationInstance.map(app -> Objects.equals(app.applicationInstanceId(), id)).orElse(false);
    }

    private boolean isHostedVespaApplicationWithPredicate(Predicate<ApplicationInstanceId> predicate) {
        return isHostedVespaTenant() &&
                applicationInstance.map(app -> predicate.test(app.applicationInstanceId())).orElse(false);
    }

    private boolean isHostedVespaTenant() {
        return applicationInstance.map(a -> Objects.equals(a.tenantId(), TenantId.HOSTED_VESPA)).orElse(false);
    }

    @Override
    public String toString() {
        return "ServiceCluster{" +
                "clusterId=" + clusterId +
                ", serviceType=" + serviceType +
                ", serviceInstances=" + serviceInstances +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceCluster that = (ServiceCluster) o;
        return Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(serviceType, that.serviceType) &&
                Objects.equals(serviceInstances, that.serviceInstances);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, serviceType, serviceInstances);
    }
}

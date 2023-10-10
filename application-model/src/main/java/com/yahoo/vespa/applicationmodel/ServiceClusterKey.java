// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class ServiceClusterKey {

    private final ClusterId clusterId;
    private final ServiceType serviceType;

    public ServiceClusterKey(ClusterId clusterId, ServiceType serviceType) {
        this.clusterId = clusterId;
        this.serviceType = serviceType;
    }

    @JsonProperty("clusterId")
    public ClusterId clusterId() {
        return clusterId;
    }

    @JsonProperty("serviceType")
    public ServiceType serviceType() {
        return serviceType;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @JsonValue
    @Override
    public String toString() {
        return clusterId.s() + ":" + serviceType.s();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceClusterKey that = (ServiceClusterKey) o;
        return Objects.equals(clusterId, that.clusterId) &&
                Objects.equals(serviceType, that.serviceType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, serviceType);
    }

}

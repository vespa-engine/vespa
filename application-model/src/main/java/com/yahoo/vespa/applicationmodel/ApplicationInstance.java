// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
public class ApplicationInstance<STATUS> {
    private final TenantId tenantId;
    private final ApplicationInstanceId applicationInstanceId;
    private final Set<ServiceCluster<STATUS>> serviceClusters;

    public ApplicationInstance(TenantId tenantId, ApplicationInstanceId applicationInstanceId, Set<ServiceCluster<STATUS>> serviceClusters) {
        this.tenantId = tenantId;
        this.applicationInstanceId = applicationInstanceId;
        this.serviceClusters = serviceClusters;
    }

    @JsonProperty("tenantId")
    public TenantId tenantId() {
        return tenantId;
    }

    @JsonProperty("applicationInstanceId")
    public ApplicationInstanceId applicationInstanceId() {
        return applicationInstanceId;
    }

    @JsonProperty("serviceClusters")
    public Set<ServiceCluster<STATUS>> serviceClusters() {
        return serviceClusters;
    }

    @JsonProperty("reference")
    public ApplicationInstanceReference reference() {
        return new ApplicationInstanceReference(tenantId, applicationInstanceId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationInstance<?> that = (ApplicationInstance<?>) o;
        return Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(applicationInstanceId, that.applicationInstanceId) &&
                Objects.equals(serviceClusters, that.serviceClusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, applicationInstanceId, serviceClusters);
    }

    @Override
    public String toString() {
        return "ApplicationInstance{" +
                "tenantId=" + tenantId +
                ", applicationInstanceId=" + applicationInstanceId +
                ", serviceClusters=" + serviceClusters +
                '}';
    }
}

// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
public class ApplicationInstance {

    private final ApplicationInstanceReference reference;
    private final Set<ServiceCluster> serviceClusters;

    public ApplicationInstance(TenantId tenantId,
                               ApplicationInstanceId applicationInstanceId,
                               Set<ServiceCluster> serviceClusters) {
        this(new ApplicationInstanceReference(tenantId, applicationInstanceId), serviceClusters);
    }

    public ApplicationInstance(ApplicationInstanceReference reference, Set<ServiceCluster> serviceClusters) {
        this.reference = reference;
        this.serviceClusters = serviceClusters;
    }

    @JsonProperty("tenantId")
    public TenantId tenantId() {
        return reference.tenantId();
    }

    @JsonProperty("applicationInstanceId")
    public ApplicationInstanceId applicationInstanceId() {
        return reference.applicationInstanceId();
    }

    @JsonProperty("serviceClusters")
    public Set<ServiceCluster> serviceClusters() {
        return serviceClusters;
    }

    @JsonProperty("reference")
    public ApplicationInstanceReference reference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationInstance that = (ApplicationInstance) o;
        return Objects.equals(reference, that.reference) &&
                Objects.equals(serviceClusters, that.serviceClusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, serviceClusters);
    }

    @Override
    public String toString() {
        return "ApplicationInstance{" +
                "reference=" + reference +
                ", serviceClusters=" + serviceClusters +
                '}';
    }

}

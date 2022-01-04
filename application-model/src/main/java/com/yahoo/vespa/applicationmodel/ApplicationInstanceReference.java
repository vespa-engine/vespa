// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
// TODO: Remove this and use ApplicationId instead (if you need it for the JSON stuff move it to that layer and don't let it leak)
public class ApplicationInstanceReference implements Comparable<ApplicationInstanceReference> {

    private final TenantId tenantId;
    private final ApplicationInstanceId applicationInstanceId;

    public ApplicationInstanceReference(TenantId tenantId, ApplicationInstanceId applicationInstanceId) {
        this.tenantId = tenantId;
        this.applicationInstanceId = applicationInstanceId;
    }

    @JsonProperty("tenantId")
    public TenantId tenantId() {
        return tenantId;
    }

    @JsonProperty("applicationInstanceId")
    public ApplicationInstanceId applicationInstanceId() {
        return applicationInstanceId;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @JsonValue
    @Override
    public String toString() {
        return asString();
    }

    public String asString() {
        return tenantId.value() + ":" + applicationInstanceId.value();
    }

    @Override
    public int compareTo(ApplicationInstanceReference o) {
        return this.asString().compareTo(o.asString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationInstanceReference that = (ApplicationInstanceReference) o;
        return Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(applicationInstanceId, that.applicationInstanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, applicationInstanceId);
    }

}

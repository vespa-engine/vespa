// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class IdentityDocument {

    @JsonProperty("provider-unique-id")
    public final ProviderUniqueId providerUniqueId;
    @JsonProperty("configserver-hostname")
    public final String configServerHostname;
    @JsonProperty("instance-hostname")
    public final String instanceHostname;
    @JsonProperty("created-at")
    public final Instant createdAt;

    public IdentityDocument(
                            @JsonProperty("provider-unique-id") ProviderUniqueId providerUniqueId,
                            @JsonProperty("configserver-hostname") String configServerHostname,
                            @JsonProperty("instance-hostname") String instanceHostname,
                            @JsonProperty("created-at") Instant createdAt) {
        this.providerUniqueId = providerUniqueId;
        this.configServerHostname = configServerHostname;
        this.instanceHostname = instanceHostname;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "IdentityDocument{" +
                "providerUniqueId=" + providerUniqueId +
                ", configServerHostname='" + configServerHostname + '\'' +
                ", instanceHostname='" + instanceHostname + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityDocument that = (IdentityDocument) o;
        return  Objects.equals(providerUniqueId, that.providerUniqueId) &&
                Objects.equals(configServerHostname, that.configServerHostname) &&
                Objects.equals(instanceHostname, that.instanceHostname) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerUniqueId, configServerHostname, instanceHostname, createdAt);
    }
}

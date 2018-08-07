// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 * @deprecated Will soon be inlined into {@link SignedIdentityDocumentEntity}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Deprecated
public class IdentityDocumentEntity {

    @JsonProperty("provider-unique-id")
    public final VespaUniqueInstanceIdEntity providerUniqueId;
    @JsonProperty("configserver-hostname")
    public final String configServerHostname;
    @JsonProperty("instance-hostname")
    public final String instanceHostname;
    @JsonProperty("created-at")
    public final Instant createdAt;
    @JsonProperty("ip-addresses")
    public final Set<String> ipAddresses;

    public IdentityDocumentEntity(
            @JsonProperty("provider-unique-id") VespaUniqueInstanceIdEntity providerUniqueId,
            @JsonProperty("configserver-hostname") String configServerHostname,
            @JsonProperty("instance-hostname") String instanceHostname,
            @JsonProperty("created-at") Instant createdAt,
            @JsonProperty("ip-addresses") Set<String> ipAddresses) {
        this.providerUniqueId = providerUniqueId;
        this.configServerHostname = configServerHostname;
        this.instanceHostname = instanceHostname;
        this.createdAt = createdAt;
        this.ipAddresses = ipAddresses;
    }


    @Override
    public String toString() {
        return "IdentityDocumentEntity{" +
                "providerUniqueId=" + providerUniqueId +
                ", configServerHostname='" + configServerHostname + '\'' +
                ", instanceHostname='" + instanceHostname + '\'' +
                ", createdAt=" + createdAt +
                ", ipAddresses=" + ipAddresses +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityDocumentEntity that = (IdentityDocumentEntity) o;
        return Objects.equals(providerUniqueId, that.providerUniqueId) &&
                Objects.equals(configServerHostname, that.configServerHostname) &&
                Objects.equals(instanceHostname, that.instanceHostname) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(ipAddresses, that.ipAddresses);
    }

    @Override
    public int hashCode() {

        return Objects.hash(providerUniqueId, configServerHostname, instanceHostname, createdAt, ipAddresses);
    }
}

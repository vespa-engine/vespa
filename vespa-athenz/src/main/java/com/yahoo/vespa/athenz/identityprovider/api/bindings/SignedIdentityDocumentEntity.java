// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedIdentityDocumentEntity {

    @JsonProperty("signature") public final String signature;
    @JsonProperty("signing-key-version") public final int signingKeyVersion;
    @JsonProperty("provider-unique-id") public final String providerUniqueId; // String representation
    @JsonProperty("provider-service") public final String providerService;
    @JsonProperty("document-version") public final int documentVersion;
    @JsonProperty("configserver-hostname") public final String configServerHostname;
    @JsonProperty("instance-hostname") public final String instanceHostname;
    @JsonProperty("created-at") public final Instant createdAt;
    @JsonProperty("ip-addresses") public final Set<String> ipAddresses;
    @JsonProperty("identity-type") public final String identityType;

    @JsonCreator
    public SignedIdentityDocumentEntity(@JsonProperty("signature") String signature,
                                        @JsonProperty("signing-key-version") int signingKeyVersion,
                                        @JsonProperty("provider-unique-id") String providerUniqueId,
                                        @JsonProperty("provider-service") String providerService,
                                        @JsonProperty("document-version") int documentVersion,
                                        @JsonProperty("configserver-hostname") String configServerHostname,
                                        @JsonProperty("instance-hostname") String instanceHostname,
                                        @JsonProperty("created-at") Instant createdAt,
                                        @JsonProperty("ip-addresses") Set<String> ipAddresses,
                                        @JsonProperty("identity-type") String identityType) {
        this.signature = signature;
        this.signingKeyVersion = signingKeyVersion;
        this.providerUniqueId = providerUniqueId;
        this.providerService = providerService;
        this.documentVersion = documentVersion;
        this.configServerHostname = configServerHostname;
        this.instanceHostname = instanceHostname;
        this.createdAt = createdAt;
        this.ipAddresses = ipAddresses;
        this.identityType = identityType;
    }

    @Override
    public String toString() {
        return "SignedIdentityDocumentEntity{" +
                ", signature='" + signature + '\'' +
                ", signingKeyVersion=" + signingKeyVersion +
                ", providerUniqueId='" + providerUniqueId + '\'' +
                ", providerService='" + providerService + '\'' +
                ", documentVersion=" + documentVersion +
                ", configServerHostname='" + configServerHostname + '\'' +
                ", instanceHostname='" + instanceHostname + '\'' +
                ", createdAt=" + createdAt +
                ", ipAddresses=" + ipAddresses +
                ", identityType=" + identityType +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignedIdentityDocumentEntity that = (SignedIdentityDocumentEntity) o;
        return signingKeyVersion == that.signingKeyVersion &&
                documentVersion == that.documentVersion &&
                Objects.equals(signature, that.signature) &&
                Objects.equals(providerUniqueId, that.providerUniqueId) &&
                Objects.equals(providerService, that.providerService) &&
                Objects.equals(configServerHostname, that.configServerHostname) &&
                Objects.equals(instanceHostname, that.instanceHostname) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(ipAddresses, that.ipAddresses) &&
                Objects.equals(identityType, that.identityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature, signingKeyVersion, providerUniqueId, providerService, documentVersion, configServerHostname, instanceHostname, createdAt, ipAddresses, identityType);
    }
}

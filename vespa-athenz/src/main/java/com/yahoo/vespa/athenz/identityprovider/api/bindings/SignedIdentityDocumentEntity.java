// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignedIdentityDocumentEntity {

    private static final ObjectMapper mapper = createObjectMapper();

    @JsonProperty("identity-document")public final String rawIdentityDocument;
    @JsonIgnore public final IdentityDocumentEntity identityDocument;
    @JsonProperty("signature") public final String signature;
    @JsonProperty("signing-key-version") public final int signingKeyVersion;
    @JsonProperty("provider-unique-id") public final String providerUniqueId; // String representation
    @JsonProperty("dns-suffix") public final String dnsSuffix;
    @JsonProperty("provider-service") public final String providerService;
    @JsonProperty("zts-endpoint") public final URI ztsEndpoint;
    @JsonProperty("document-version") public final int documentVersion;
    @JsonProperty("configserver-hostname") public final String configServerHostname;
    @JsonProperty("instance-hostname") public final String instanceHostname;
    @JsonProperty("created-at") public final Instant createdAt;
    @JsonProperty("ip-addresses") public final Set<String> ipAddresses;
    @JsonProperty("identity-type") public final String identityType;

    @JsonCreator
    public SignedIdentityDocumentEntity(@JsonProperty("identity-document") String rawIdentityDocument,
                                        @JsonProperty("signature") String signature,
                                        @JsonProperty("signing-key-version") int signingKeyVersion,
                                        @JsonProperty("provider-unique-id") String providerUniqueId,
                                        @JsonProperty("dns-suffix") String dnsSuffix,
                                        @JsonProperty("provider-service") String providerService,
                                        @JsonProperty("zts-endpoint") URI ztsEndpoint,
                                        @JsonProperty("document-version") int documentVersion,
                                        @JsonProperty("configserver-hostname") String configServerHostname,
                                        @JsonProperty("instance-hostname") String instanceHostname,
                                        @JsonProperty("created-at") Instant createdAt,
                                        @JsonProperty("ip-addresses") Set<String> ipAddresses,
                                        @JsonProperty("identity-type") String identityType) {
        this.rawIdentityDocument = rawIdentityDocument;
        this.identityDocument = parseIdentityDocument(rawIdentityDocument);
        this.signature = signature;
        this.signingKeyVersion = signingKeyVersion;
        this.providerUniqueId = providerUniqueId;
        this.dnsSuffix = dnsSuffix;
        this.providerService = providerService;
        this.ztsEndpoint = ztsEndpoint;
        this.documentVersion = documentVersion;
        this.configServerHostname = configServerHostname;
        this.instanceHostname = instanceHostname;
        this.createdAt = createdAt;
        this.ipAddresses = ipAddresses;
        this.identityType = identityType;
    }

    private static IdentityDocumentEntity parseIdentityDocument(String rawIdentityDocument) {
        try {
            return mapper.readValue(Base64.getDecoder().decode(rawIdentityDocument), IdentityDocumentEntity.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    public String toString() {
        return "SignedIdentityDocumentEntity{" +
                "rawIdentityDocument='" + rawIdentityDocument + '\'' +
                ", identityDocument=" + identityDocument +
                ", signature='" + signature + '\'' +
                ", signingKeyVersion=" + signingKeyVersion +
                ", providerUniqueId='" + providerUniqueId + '\'' +
                ", dnsSuffix='" + dnsSuffix + '\'' +
                ", providerService='" + providerService + '\'' +
                ", ztsEndpoint=" + ztsEndpoint +
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
                Objects.equals(rawIdentityDocument, that.rawIdentityDocument) &&
                Objects.equals(identityDocument, that.identityDocument) &&
                Objects.equals(signature, that.signature) &&
                Objects.equals(providerUniqueId, that.providerUniqueId) &&
                Objects.equals(dnsSuffix, that.dnsSuffix) &&
                Objects.equals(providerService, that.providerService) &&
                Objects.equals(ztsEndpoint, that.ztsEndpoint) &&
                Objects.equals(configServerHostname, that.configServerHostname) &&
                Objects.equals(instanceHostname, that.instanceHostname) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(ipAddresses, that.ipAddresses) &&
                Objects.equals(identityType, identityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawIdentityDocument, identityDocument, signature, signingKeyVersion, providerUniqueId, dnsSuffix, providerService, ztsEndpoint, documentVersion, configServerHostname, instanceHostname, createdAt, ipAddresses, identityType);
    }
}

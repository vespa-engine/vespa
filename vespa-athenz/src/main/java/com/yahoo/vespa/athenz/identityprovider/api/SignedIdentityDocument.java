// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A signed identity document
 *
 * @author bjorncs
 */
public class SignedIdentityDocument {
    public static final int DEFAULT_DOCUMENT_VERSION = 1;

    private final String signature;
    private final int signingKeyVersion;
    private final VespaUniqueInstanceId providerUniqueId;
    private final AthenzService providerService;
    private final int documentVersion;
    private final String configServerHostname;
    private final String instanceHostname;
    private final Instant createdAt;
    private final Set<String> ipAddresses;
    private final IdentityType identityType;

    public SignedIdentityDocument(String signature,
                                  int signingKeyVersion,
                                  VespaUniqueInstanceId providerUniqueId,
                                  AthenzService providerService,
                                  int documentVersion,
                                  String configServerHostname,
                                  String instanceHostname,
                                  Instant createdAt,
                                  Set<String> ipAddresses,
                                  IdentityType identityType) {
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

    public String signature() {
        return signature;
    }

    public int signingKeyVersion() {
        return signingKeyVersion;
    }

    public VespaUniqueInstanceId providerUniqueId() {
        return providerUniqueId;
    }

    public AthenzService providerService() {
        return providerService;
    }

    public int documentVersion() {
        return documentVersion;
    }

    public String configServerHostname() {
        return configServerHostname;
    }

    public String instanceHostname() {
        return instanceHostname;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<String> ipAddresses() {
        return ipAddresses;
    }

    public IdentityType identityType() {
        return identityType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignedIdentityDocument that = (SignedIdentityDocument) o;
        return signingKeyVersion == that.signingKeyVersion &&
               documentVersion == that.documentVersion &&
               Objects.equals(signature, that.signature) &&
               Objects.equals(providerUniqueId, that.providerUniqueId) &&
               Objects.equals(providerService, that.providerService) &&
               Objects.equals(configServerHostname, that.configServerHostname) &&
               Objects.equals(instanceHostname, that.instanceHostname) &&
               Objects.equals(createdAt, that.createdAt) &&
               Objects.equals(ipAddresses, that.ipAddresses) &&
               identityType == that.identityType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature, signingKeyVersion, providerUniqueId, providerService, documentVersion, configServerHostname, instanceHostname, createdAt, ipAddresses, identityType);
    }

    @Override
    public String toString() {
        return "SignedIdentityDocument{" +
               "signature='" + signature + '\'' +
               ", signingKeyVersion=" + signingKeyVersion +
               ", providerUniqueId=" + providerUniqueId +
               ", providerService=" + providerService +
               ", documentVersion=" + documentVersion +
               ", configServerHostname='" + configServerHostname + '\'' +
               ", instanceHostname='" + instanceHostname + '\'' +
               ", createdAt=" + createdAt +
               ", ipAddresses=" + ipAddresses +
               ", identityType=" + identityType +
               '}';
    }
}

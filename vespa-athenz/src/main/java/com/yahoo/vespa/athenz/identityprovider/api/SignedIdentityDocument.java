// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.time.Instant;
import java.util.Set;

/**
 * A signed identity document
 *
 * @author bjorncs
 */
public class SignedIdentityDocument {
    public static final int DEFAULT_KEY_VERSION = 0;
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
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.net.URI;

/**
 * A signed identity document which contains a {@link IdentityDocument}
 *
 * @author bjorncs
 */
public class SignedIdentityDocument {
    public static final int DEFAULT_KEY_VERSION = 0;
    public static final int DEFAULT_DOCUMENT_VERSION = 1;

    private final IdentityDocument identityDocument;
    private final String signature;
    private final int signingKeyVersion;
    private final VespaUniqueInstanceId providerUniqueId;
    private final String dnsSuffix;
    private final AthenzService providerService;
    private final URI ztsEndpoint;
    private final int documentVersion;

    public SignedIdentityDocument(IdentityDocument identityDocument,
                                  String signature,
                                  int signingKeyVersion,
                                  VespaUniqueInstanceId providerUniqueId,
                                  String dnsSuffix,
                                  AthenzService providerService,
                                  URI ztsEndpoint,
                                  int documentVersion) {
        this.identityDocument = identityDocument;
        this.signature = signature;
        this.signingKeyVersion = signingKeyVersion;
        this.providerUniqueId = providerUniqueId;
        this.dnsSuffix = dnsSuffix;
        this.providerService = providerService;
        this.ztsEndpoint = ztsEndpoint;
        this.documentVersion = documentVersion;
    }

    public IdentityDocument identityDocument() {
        return identityDocument;
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

    public String dnsSuffix() {
        return dnsSuffix;
    }

    public AthenzService providerService() {
        return providerService;
    }

    public URI ztsEndpoint() {
        return ztsEndpoint;
    }

    public int documentVersion() {
        return documentVersion;
    }
}

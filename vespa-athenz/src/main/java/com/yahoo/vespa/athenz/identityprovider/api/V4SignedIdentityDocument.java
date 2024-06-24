// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

public record V4SignedIdentityDocument(String signature, int v4SigningKeyVersion, int documentVersion,
                                            String data, IdentityDocument identityDocument) implements SignedIdentityDocument {

    public V4SignedIdentityDocument {
        identityDocument = EntityBindingsMapper.fromIdentityDocumentData(data);
    }

    public V4SignedIdentityDocument(String signature, int v4SigningKeyVersion, int documentVersion, String data) {
        this(signature, v4SigningKeyVersion, documentVersion, data, null);
    }

    @Override
    public String signingKeyVersion() {
        return Integer.toString(v4SigningKeyVersion);
    }
}

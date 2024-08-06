// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.athenz.identityprovider.api;

public record V5SignedIdentityDocument(String signature, String signingKeyVersion, int documentVersion,
                                           String data, IdentityDocument identityDocument) implements SignedIdentityDocument {


    public V5SignedIdentityDocument {
        identityDocument = EntityBindingsMapper.fromIdentityDocumentData(data);
    }

    public V5SignedIdentityDocument(String signature, String signingKeyVersion, int documentVersion, String data) {
        this(signature,signingKeyVersion,documentVersion, data, null);
    }
}

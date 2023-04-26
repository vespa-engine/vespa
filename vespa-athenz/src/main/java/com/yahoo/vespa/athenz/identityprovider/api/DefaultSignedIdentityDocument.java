// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

public record DefaultSignedIdentityDocument(String signature, int signingKeyVersion, int documentVersion,
                                            String data, IdentityDocument identityDocument) implements SignedIdentityDocument {

    public DefaultSignedIdentityDocument {
        identityDocument = EntityBindingsMapper.fromIdentityDocumentData(data);
    }

    public DefaultSignedIdentityDocument(String signature, int signingKeyVersion, int documentVersion, String data) {
        this(signature,signingKeyVersion,documentVersion, data, null);
    }
}

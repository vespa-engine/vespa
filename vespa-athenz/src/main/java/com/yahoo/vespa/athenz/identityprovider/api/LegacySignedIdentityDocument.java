// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

public record LegacySignedIdentityDocument(String signature, int signingKeyVersion, int documentVersion,
                                           IdentityDocument identityDocument) implements SignedIdentityDocument {
}

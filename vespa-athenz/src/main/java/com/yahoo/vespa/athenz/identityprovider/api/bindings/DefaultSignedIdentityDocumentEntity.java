// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DefaultSignedIdentityDocumentEntity(
        @JsonProperty("signature") String signature,
        @JsonProperty("signing-key-version") int signingKeyVersion,
        @JsonProperty("document-version") int documentVersion,
        @JsonProperty("data") String data)
        implements SignedIdentityDocumentEntity {
}

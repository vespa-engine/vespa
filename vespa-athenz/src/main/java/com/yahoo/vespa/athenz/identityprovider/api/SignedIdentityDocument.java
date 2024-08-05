// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

/**
 * A signed identity document.
 * @author bjorncs
 * @author mortent
 */
public interface SignedIdentityDocument {

    int LEGACY_DOCUMENT_VERSION = 4;
    int DEFAULT_DOCUMENT_VERSION = 5;

    default boolean outdated() { return documentVersion() < DEFAULT_DOCUMENT_VERSION; }

    IdentityDocument identityDocument();
    String signature();
    String signingKeyVersion();
    int documentVersion();
    String data();
}

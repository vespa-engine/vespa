// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

/**
 * A client that communicates that fetches an identity document.
 *
 * @author bjorncs
 */
public interface IdentityDocumentClient {
    SignedIdentityDocument getNodeIdentityDocument(String host);
    SignedIdentityDocument getTenantIdentityDocument(String host);
}

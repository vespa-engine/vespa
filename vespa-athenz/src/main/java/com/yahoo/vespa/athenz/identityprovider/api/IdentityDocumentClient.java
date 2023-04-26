// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import java.util.OptionalInt;

/**
 * A client that communicates that fetches an identity document.
 *
 * @author bjorncs
 */
public interface IdentityDocumentClient {
    SignedIdentityDocument getNodeIdentityDocument(String host, int documentVersion);
    SignedIdentityDocument getTenantIdentityDocument(String host, int documentVersion);
}

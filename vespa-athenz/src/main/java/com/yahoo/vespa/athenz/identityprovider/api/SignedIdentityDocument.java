// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.time.Instant;
import java.util.Set;

/**
 * A signed identity document
 *
 * @author bjorncs
 */
public record SignedIdentityDocument(String signature, int signingKeyVersion, VespaUniqueInstanceId providerUniqueId,
                                     AthenzService providerService, int documentVersion, String configServerHostname,
                                     String instanceHostname, Instant createdAt, Set<String> ipAddresses,
                                     IdentityType identityType, ClusterType clusterType) {
    public static final int DEFAULT_DOCUMENT_VERSION = 2;

}

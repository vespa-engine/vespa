// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A signed identity document.
 * The {@link #unknownAttributes()} member provides forward compatibility and ensures any new/unknown fields are kept intact when serialized to JSON.
 *
 * @author bjorncs
 */
public record SignedIdentityDocument(String signature, int signingKeyVersion, VespaUniqueInstanceId providerUniqueId,
                                     AthenzService providerService, int documentVersion, String configServerHostname,
                                     String instanceHostname, Instant createdAt, Set<String> ipAddresses,
                                     IdentityType identityType, ClusterType clusterType, Map<String, Object> unknownAttributes) {

    public SignedIdentityDocument {
        ipAddresses = Set.copyOf(ipAddresses);

        Map<String, Object> nonNull = new HashMap<>();
        unknownAttributes.forEach((key, value) -> {
            if (value != null) nonNull.put(key, value);
        });
        // Map.copyOf() does not allow null values
        unknownAttributes = Map.copyOf(nonNull);
    }

    public SignedIdentityDocument(String signature, int signingKeyVersion, VespaUniqueInstanceId providerUniqueId,
                                  AthenzService providerService, int documentVersion, String configServerHostname,
                                  String instanceHostname, Instant createdAt, Set<String> ipAddresses,
                                  IdentityType identityType, ClusterType clusterType) {
        this(signature, signingKeyVersion, providerUniqueId, providerService, documentVersion, configServerHostname,
            instanceHostname, createdAt, ipAddresses, identityType, clusterType, Map.of());
    }

    public static final int DEFAULT_DOCUMENT_VERSION = 2;

    public boolean outdated() { return documentVersion < DEFAULT_DOCUMENT_VERSION; }

}

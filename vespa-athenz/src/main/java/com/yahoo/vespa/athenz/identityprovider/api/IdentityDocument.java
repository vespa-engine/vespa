// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an unsigned identity document
 * @author mortent
 */
public record IdentityDocument(VespaUniqueInstanceId providerUniqueId, AthenzService providerService, String configServerHostname,
                               String instanceHostname, Instant createdAt, Set<String> ipAddresses,
                               IdentityType identityType, ClusterType clusterType, String ztsUrl,
                               AthenzIdentity serviceIdentity, Map<String, Object> unknownAttributes) {

    public IdentityDocument {
        ipAddresses = Set.copyOf(ipAddresses);

        Map<String, Object> nonNull = new HashMap<>();
        unknownAttributes.forEach((key, value) -> {
            if (value != null) nonNull.put(key, value);
        });
        // Map.copyOf() does not allow null values
        unknownAttributes = Map.copyOf(nonNull);
    }

    public IdentityDocument(VespaUniqueInstanceId providerUniqueId, AthenzService providerService, String configServerHostname,
                            String instanceHostname, Instant createdAt, Set<String> ipAddresses,
                            IdentityType identityType, ClusterType clusterType, String ztsUrl,
                            AthenzIdentity serviceIdentity) {
        this(providerUniqueId, providerService, configServerHostname, instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity, Map.of());
    }


    public IdentityDocument withServiceIdentity(AthenzService athenzService) {
        return new IdentityDocument(
                this.providerUniqueId,
                this.providerService,
                this.configServerHostname,
                this.instanceHostname,
                this.createdAt,
                this.ipAddresses,
                this.identityType,
                this.clusterType,
                this.ztsUrl,
                athenzService,
                this.unknownAttributes);
    }
}

// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.api;

import java.time.Instant;
import java.util.Set;

/**
 * The identity document that contains the instance specific information
 *
 * @author bjorncs
 * @deprecated Will soon be inlined into {@link SignedIdentityDocument}
 */
@Deprecated
public class IdentityDocument {
    private final VespaUniqueInstanceId providerUniqueId;
    private final String configServerHostname;
    private final String instanceHostname;
    private final Instant createdAt;
    private final Set<String> ipAddresses;

    public IdentityDocument(VespaUniqueInstanceId providerUniqueId,
                            String configServerHostname,
                            String instanceHostname,
                            Instant createdAt,
                            Set<String> ipAddresses) {
        this.providerUniqueId = providerUniqueId;
        this.configServerHostname = configServerHostname;
        this.instanceHostname = instanceHostname;
        this.createdAt = createdAt;
        this.ipAddresses = ipAddresses;
    }

    public VespaUniqueInstanceId providerUniqueId() {
        return providerUniqueId;
    }

    public String configServerHostname() {
        return configServerHostname;
    }

    public String instanceHostname() {
        return instanceHostname;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Set<String> ipAddresses() {
        return ipAddresses;
    }
}

// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;

import java.net.URI;
import java.util.Optional;

/**
 * Represents an operation to update or unset the archive URI value for a given tenant or cloud account.
 *
 * @author freva
 */
public class ArchiveUriUpdate {
    private final Optional<TenantName> tenantName;
    private final Optional<CloudAccount> cloudAccount;
    private final Optional<URI> archiveUri;

    private ArchiveUriUpdate(Optional<TenantName> tenantName, Optional<CloudAccount> cloudAccount, Optional<URI> archiveUri) {
        this.tenantName = tenantName;
        this.cloudAccount = cloudAccount;
        this.archiveUri = archiveUri;
    }

    public Optional<TenantName> tenantName() { return tenantName; }
    public Optional<CloudAccount> cloudAccount() { return cloudAccount; }
    public Optional<URI> archiveUri() { return archiveUri; }

    public static ArchiveUriUpdate setArchiveUriFor(TenantName tenantName, URI archiveUri) {
        return new ArchiveUriUpdate(Optional.of(tenantName), Optional.empty(), Optional.of(archiveUri));
    }
    public static ArchiveUriUpdate deleteArchiveUriFor(TenantName tenantName) {
        return new ArchiveUriUpdate(Optional.of(tenantName), Optional.empty(), Optional.empty());
    }

    public static ArchiveUriUpdate setArchiveUriFor(CloudAccount cloudAccount, URI archiveUri) {
        return new ArchiveUriUpdate(Optional.empty(), Optional.of(cloudAccount), Optional.of(archiveUri));
    }
    public static ArchiveUriUpdate deleteArchiveUriFor(CloudAccount cloudAccount) {
        return new ArchiveUriUpdate(Optional.empty(), Optional.of(cloudAccount), Optional.empty());
    }
}

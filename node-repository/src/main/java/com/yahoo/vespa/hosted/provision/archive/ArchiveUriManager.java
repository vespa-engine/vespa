// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.archive;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.CachedSupplier;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Thread safe class to get and set archive URI for given account and tenants.
 *
 * @author freva
 */
public class ArchiveUriManager {

    private static final Duration cacheTtl = Duration.ofMinutes(1);

    private final CuratorDb db;
    private final Zone zone;
    private final CachedSupplier<ArchiveUris> archiveUris;

    public ArchiveUriManager(CuratorDb db, Zone zone) {
        this.db = db;
        this.zone = zone;
        this.archiveUris = new CachedSupplier<>(db::readArchiveUris, cacheTtl);
    }

    public ArchiveUris archiveUris() {
        return archiveUris.get();
    }

    /** Returns the archive URI to use for given node */
    public Optional<String> archiveUriFor(Node node) {
        if (node.allocation().isEmpty()) return Optional.empty();
        ApplicationId app = node.allocation().get().owner();

        return Optional.ofNullable(node.cloudAccount().isEnclave(zone) ?
                archiveUris.get().accountArchiveUris().get(node.cloudAccount()) :
                archiveUris.get().tenantArchiveUris().get(app.tenant()))
                .map(uri -> {
                    // TODO (freva): Remove when all URIs dont have tenant name in them anymore
                    String tenantSuffix = "/" + app.tenant().value() + "/";
                    if (uri.endsWith(tenantSuffix)) return uri.substring(0, uri.length() - tenantSuffix.length() + 1);
                    return uri;
                })
                .map(uri -> {
                    StringBuilder sb = new StringBuilder(100).append(uri)
                            .append(app.tenant().value()).append('/')
                            .append(app.application().value()).append('/')
                            .append(app.instance().value()).append('/')
                            .append(node.allocation().get().membership().cluster().id().value()).append('/');

                    for (char c: node.hostname().toCharArray()) {
                        if (c == '.') break;
                        sb.append(c);
                    }

                    return sb.append('/').toString();
                });
    }

    /** Set (or remove, if archiveURI is empty) archive URI to use for given tenant */
    public void setArchiveUri(TenantName tenant, Optional<String> archiveUri) {
        setArchiveUri(au -> au.with(tenant, archiveUri));
    }

    /** Set (or remove, if archiveURI is empty) archive URI to use for given account */
    public void setArchiveUri(CloudAccount account, Optional<String> archiveUri) {
        if (!account.isEnclave(zone) || account.isUnspecified())
            throw new IllegalArgumentException("Cannot set archive URI for non-enclave account: " + account);
        setArchiveUri(au -> au.with(account, archiveUri));
    }

    private void setArchiveUri(Function<ArchiveUris, ArchiveUris> mapper) {
        try (Lock lock = db.lockArchiveUris()) {
            ArchiveUris archiveUris = db.readArchiveUris();
            ArchiveUris updated = mapper.apply(archiveUris);
            if (archiveUris.equals(updated)) return; // No change

            db.writeArchiveUris(updated);
            this.archiveUris.invalidate(); // Throw away current cache
        }
    }

}

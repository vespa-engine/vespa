// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.lang.CachedSupplier;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Thread safe class to get and set archive URI for given tenants. Archive URIs are stored in ZooKeeper so that
 * nodes within the same tenant have the same archive URI from all the config servers.
 *
 * @author freva
 */
public class ArchiveUris {

    private static final Logger log = Logger.getLogger(ArchiveUris.class.getName());
    private static final Pattern validUriPattern = Pattern.compile("[a-z0-9]+://(?:(?:[a-z0-9]+(?:[-_][a-z0-9.]+)*)+/)+");
    private static final Duration cacheTtl = Duration.ofMinutes(1);

    private final CuratorDb db;
    private final CachedSupplier<Map<TenantName, String>> archiveUris;
    private final Zone zone;

    public ArchiveUris(CuratorDb db, Zone zone) {
        this.db = db;
        this.archiveUris = new CachedSupplier<>(db::readArchiveUris, cacheTtl);
        this.zone = zone;
    }

    /** Returns the current archive URI for each tenant */
    public Map<TenantName, String> getArchiveUris() {
        return archiveUris.get();
    }

    /** Returns the archive URI to use for given tenant */
    private Optional<String> archiveUriFor(TenantName tenant) {
        return Optional.ofNullable(archiveUris.get().get(tenant));
    }

    /** Returns the archive URI to use for given node */
    public Optional<String> archiveUriFor(Node node) {
        if (node.cloudAccount().isEnclave(zone)) return Optional.empty(); // TODO (freva): Implement for exclave

        return node.allocation().map(Allocation::owner)
                .flatMap(app -> archiveUriFor(app.tenant())
                        .map(uri -> {
                            StringBuilder sb = new StringBuilder(100).append(uri)
                                    .append(app.application().value()).append('/')
                                    .append(app.instance().value()).append('/')
                                    .append(node.allocation().get().membership().cluster().id().value()).append('/');

                            for (char c: node.hostname().toCharArray()) {
                                if (c == '.') break;
                                sb.append(c);
                            }

                            return sb.append('/').toString();
                        }));
    }

    /** Set (or remove, if archiveURI is empty) archive URI to use for given tenant */
    public void setArchiveUri(TenantName tenant, Optional<String> archiveUri) {
        try (Lock lock = db.lockArchiveUris()) {
            Map<TenantName, String> archiveUris = new TreeMap<>(db.readArchiveUris());
            if (Optional.ofNullable(archiveUris.get(tenant)).equals(archiveUri)) return; // No change

            archiveUri.map(ArchiveUris::normalizeUri).ifPresentOrElse(uri -> archiveUris.put(tenant, uri),
                                                                      () -> archiveUris.remove(tenant));
            db.writeArchiveUris(archiveUris);
            this.archiveUris.invalidate(); // Throw away current cache
            log.log(Level.FINE, () -> archiveUri.map(s -> "Set archive URI for " + tenant + " to " + s)
                                                .orElseGet(() -> "Remove archive URI for " + tenant));
        }
    }

    static String normalizeUri(String uri) {
        if (!uri.endsWith("/")) uri = uri + "/";
        if (!validUriPattern.matcher(uri).matches())
            throw new IllegalArgumentException("Invalid archive URI: " + uri);
        return uri;
    }
}

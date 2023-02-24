package com.yahoo.vespa.hosted.provision.archive;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public record ArchiveUris(Map<TenantName, String> tenantArchiveUris, Map<CloudAccount, String> accountArchiveUris) {
    private static final Pattern validUriPattern = Pattern.compile("[a-z0-9]+://(?:(?:[a-z0-9]+(?:[-_][a-z0-9.]+)*)+/)+");

    public ArchiveUris(Map<TenantName, String> tenantArchiveUris, Map<CloudAccount, String> accountArchiveUris) {
        this.tenantArchiveUris = Map.copyOf(tenantArchiveUris);
        this.accountArchiveUris = Map.copyOf(accountArchiveUris);
    }

    public ArchiveUris with(TenantName tenant, Optional<String> archiveUri) {
        Map<TenantName, String> updated = updateIfChanged(tenantArchiveUris, tenant, archiveUri);
        if (updated == tenantArchiveUris) return this;
        return new ArchiveUris(updated, accountArchiveUris);
    }

    public ArchiveUris with(CloudAccount account, Optional<String> archiveUri) {
        Map<CloudAccount, String> updated = updateIfChanged(accountArchiveUris, account, archiveUri);
        if (updated == accountArchiveUris) return this;
        return new ArchiveUris(tenantArchiveUris, updated);
    }

    private static <T> Map<T, String> updateIfChanged(Map<T, String> current, T key, Optional<String> archiveUri) {
        archiveUri = archiveUri.map(ArchiveUris::normalizeUri);
        if (Optional.ofNullable(current.get(key)).equals(archiveUri)) return current;
        Map<T, String> updated = new HashMap<>(current);
        archiveUri.ifPresentOrElse(uri -> updated.put(key, uri), () -> updated.remove(key));
        return updated;
    }

    static String normalizeUri(String uri) {
        if (!uri.endsWith("/")) uri = uri + "/";
        if (!validUriPattern.matcher(uri).matches())
            throw new IllegalArgumentException("Invalid archive URI: " + uri);
        return uri;
    }
}

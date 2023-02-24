package com.yahoo.vespa.hosted.provision.archive;


import com.yahoo.config.provision.TenantName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.hosted.provision.archive.ArchiveUris.normalizeUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveUrisTest {

    @Test
    void normalize_test() {
        assertEquals("ftp://domain/legal-dir123/", normalizeUri("ftp://domain/legal-dir123"));
        assertEquals("ftp://domain/legal-dir123/", normalizeUri("ftp://domain/legal-dir123/"));
        assertEquals("s3://my-bucket-prod.region/my-tenant-123/", normalizeUri("s3://my-bucket-prod.region/my-tenant-123/"));
        assertEquals("s3://my-bucket-prod.region/my-tenant_123/", normalizeUri("s3://my-bucket-prod.region/my-tenant_123/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("domain/dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp:/domain/dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp:/domain//dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal:dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/-illegal-dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/_illegal-dir/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal-dir-/"));
        assertThrows(IllegalArgumentException.class, () -> normalizeUri("ftp://domain/illegal-dir_/"));
    }

    @Test
    void updates_in_place_if_possible() {
        TenantName t1 = TenantName.from("t1");

        ArchiveUris uris0 = new ArchiveUris(Map.of(), Map.of());
        ArchiveUris uris1 = uris0.with(t1, Optional.empty());
        assertSame(uris0, uris1);

        ArchiveUris uris2 = uris0.with(t1, Optional.of("scheme://test123"));
        assertEquals(Map.of(t1, "scheme://test123/"), uris2.tenantArchiveUris());

        assertSame(uris2, uris2.with(t1, Optional.of("scheme://test123")));
        assertSame(uris2, uris2.with(t1, Optional.of("scheme://test123/")));
        assertEquals(uris0, uris2.with(t1, Optional.empty()));
    }

}

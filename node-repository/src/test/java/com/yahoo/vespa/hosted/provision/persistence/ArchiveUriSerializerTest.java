// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.provision.archive.ArchiveUris;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class ArchiveUriSerializerTest {

    @Test
    public void test_serialization() {
        ArchiveUris archiveUris = new ArchiveUris(Map.of(
                    TenantName.from("tenant1"), "ftp://host123.test/dir/",
                    TenantName.from("tenant2"), "ftp://archive.test/vespa/"),
                Map.of(CloudAccount.from("321456987012"), "ftp://host123.test/dir/"));

        ArchiveUris serialized = ArchiveUriSerializer.fromJson(ArchiveUriSerializer.toJson(archiveUris));
        assertEquals(archiveUris, serialized);
    }

}
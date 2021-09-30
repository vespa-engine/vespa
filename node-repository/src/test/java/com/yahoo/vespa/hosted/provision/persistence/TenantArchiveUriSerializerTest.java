// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.TenantName;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class TenantArchiveUriSerializerTest {

    @Test
    public void test_serialization() {
        Map<TenantName, String> archiveUris = new TreeMap<>();
        archiveUris.put(TenantName.from("tenant1"), "ftp://host123.test/dir/");
        archiveUris.put(TenantName.from("tenant2"), "ftp://archive.test/vespa/");

        Map<TenantName, String> serialized = TenantArchiveUriSerializer.fromJson(TenantArchiveUriSerializer.toJson(archiveUris));
        assertEquals(archiveUris, serialized);
    }

}
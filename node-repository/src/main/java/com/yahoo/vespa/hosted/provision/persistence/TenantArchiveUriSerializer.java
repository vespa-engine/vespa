// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializer for archive URIs that are set per tenant.
 *
 * @author freva
 */
public class TenantArchiveUriSerializer {

    private TenantArchiveUriSerializer() {}

    public static byte[] toJson(Map<TenantName, String> archiveUrisByTenantName) {
        Slime slime = new Slime();
        Cursor object = slime.setObject();
        archiveUrisByTenantName.forEach((tenantName, archiveUri) ->
                object.setString(tenantName.value(), archiveUri));
        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<TenantName, String> fromJson(byte[] data) {
        Map<TenantName, String> archiveUrisByTenantName = new TreeMap<>(); // Use TreeMap to sort by tenant name
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();
        inspector.traverse((ObjectTraverser) (key, value) ->
                archiveUrisByTenantName.put(TenantName.from(key), value.asString()));
        return archiveUrisByTenantName;
    }

}

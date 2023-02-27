// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.archive.ArchiveUris;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializer for archive URIs that are set per tenant and per account.
 *
 * @author freva
 */
public class ArchiveUriSerializer {

    private ArchiveUriSerializer() {}

    public static byte[] toJson(ArchiveUris archiveUris) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        Cursor tenantObject = root.setObject("tenant");
        archiveUris.tenantArchiveUris().forEach((tenant, uri) -> tenantObject.setString(tenant.value(), uri));

        Cursor accountObject = root.setObject("account");
        archiveUris.accountArchiveUris().forEach((account, uri) -> accountObject.setString(account.value(), uri));

        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ArchiveUris fromJson(byte[] data) {
        Inspector inspector = SlimeUtils.jsonToSlime(data).get();

        Map<TenantName, String> tenantArchiveUris = new HashMap<>();
        inspector.field("tenant").traverse((ObjectTraverser) (key, value) -> tenantArchiveUris.put(TenantName.from(key), value.asString()));

        Map<CloudAccount, String> accountArchiveUris = new HashMap<>();
        inspector.field("account").traverse((ObjectTraverser) (key, value) -> accountArchiveUris.put(CloudAccount.from(key), value.asString()));

        return new ArchiveUris(tenantArchiveUris, accountArchiveUris);
    }

}

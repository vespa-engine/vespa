// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author olaa
 */
public class TenantSecretStoreSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private final static String awsIdField = "awsId";
    private final static String roleField = "role";
    private final static String nameField = "name";
    private static final String externalIdField = "externalId";

    public static Slime toSlime(List<TenantSecretStore> tenantSecretStores) {
        Slime slime = new Slime();
        Cursor cursor = slime.setArray();
        toSlime(tenantSecretStores, cursor);
        return slime;
    }

    public static void toSlime(List<TenantSecretStore> tenantSecretStores, Cursor cursor) {
        tenantSecretStores.forEach(tenantSecretStore -> toSlime(tenantSecretStore, cursor.addObject()));
    }

    public static void toSlime(TenantSecretStore tenantSecretStore, Cursor object) {
        object.setString(awsIdField, tenantSecretStore.getAwsId());
        object.setString(nameField, tenantSecretStore.getName());
        object.setString(roleField, tenantSecretStore.getRole());
        tenantSecretStore.getExternalId().ifPresent(
                externalId -> object.setString(externalIdField, externalId));
    }

    public static TenantSecretStore fromSlime(Inspector inspector) {
        if (inspector.type() == Type.OBJECT) {
            return new TenantSecretStore(
                    inspector.field(nameField).asString(),
                    inspector.field(awsIdField).asString(),
                    inspector.field(roleField).asString(),
                    optionalString(inspector.field(externalIdField))
            );
        }
        throw new IllegalArgumentException("Unknown format encountered for endpoint certificate metadata!");
    }

    public static List<TenantSecretStore> listFromSlime(Inspector inspector) {
        List<TenantSecretStore> tenantSecretStores = new ArrayList<>();
        inspector.traverse(((ArrayTraverser)(idx, store) -> tenantSecretStores.add(fromSlime(store))));
        return tenantSecretStores;
    }

    private static Optional<String> optionalString(Inspector object) {
        if (! object.valid()) return Optional.empty();
        return Optional.of(object.asString());
    }

}
